package com.ilmerya.server

import java.nio.file.Files
import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class GameCenterStoreTest {
    private val f = GameCenterFixture
    @Test fun signedAppleLoginRestoresInventoryAndAllowsIndependentSessionsWithoutMergingGoogle() = WeeklyStore(":memory:").use { store ->
        val google = store.loginGoogle(VerifiedPlayPlayer("T:fixture-player", "Same name"), f.now)
        val first = store.loginGameCenter(f.verifier().verify(f.proof(), f.now), f.now)
        val id = first.getString("id")
        val second = store.loginGameCenter(f.verifier().verify(f.proof(saltValue = "second-proof"), f.now), f.now + 1)
        assertEquals(id, second.getString("id"))
        assertNotEquals(google.getString("id"), id)
        assertEquals("game_center", second.getString("provider"))
        assertTrue(store.isVerifiedPlayer(id)); assertFalse(store.isGooglePlayer(id))
        assertEquals("game_center", second.getString("provider"))
        assertEquals(id, store.authenticate(first.getString("token"), f.now + 2))
        assertEquals(id, store.authenticate(second.getString("token"), f.now + 2))
        assertEquals(401, assertFailsWith<ApiProblem> { store.authenticate(first.getString("token"), f.now + 31L * 86400000) }.status)
    }

    @Test fun replayPersistsAcrossRestartAndAccountDeletionRevokesEverySession() {
        val path = Files.createTempFile("apple-auth-", ".sqlite")
        try {
            val proof = f.verifier().verify(f.proof(), f.now)
            val first = WeeklyStore(path.toString()).use { it.loginGameCenter(proof, f.now) }
            WeeklyStore(path.toString()).use { store ->
                assertEquals(409, assertFailsWith<ApiProblem> { store.loginGameCenter(proof, f.now + 1) }.status)
                val second = store.loginGameCenter(f.verifier().verify(f.proof(saltValue = "second"), f.now), f.now + 2)
                store.deleteAccount(first.getString("id"))
                listOf(first, second).forEach {
                    assertEquals(401, assertFailsWith<ApiProblem> { store.authenticate(it.getString("token"), f.now + 3) }.status)
                }
                assertEquals(409, assertFailsWith<ApiProblem> { store.loginGameCenter(proof, f.now + 3) }.status)
                val replacement = store.loginGameCenter(f.verifier().verify(f.proof(saltValue = "third"), f.now), f.now + 4)
                assertNotEquals(first.getString("id"), replacement.getString("id"))
            }
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun replayConsumptionIsAtomicAcrossTwoDatabaseConnections() {
        val path = Files.createTempFile("apple-concurrent-", ".sqlite")
        val executor = Executors.newFixedThreadPool(2)
        try {
            WeeklyStore(path.toString()).use { first -> WeeklyStore(path.toString()).use { second ->
                val identity = f.verifier().verify(f.proof(), f.now)
                val results = executor.invokeAll(listOf(first, second).map { store -> Callable {
                    try { store.loginGameCenter(identity, f.now); 200 } catch (problem: ApiProblem) { problem.status }
                } }).map { it.get() }.sorted()
                assertEquals(listOf(200, 409), results)
            } }
        } finally { executor.shutdownNow(); Files.deleteIfExists(path) }
    }

    @Test fun additiveMigrationPreservesGoogleIdAliasesSessionsAndDeletion() {
        val path = Files.createTempFile("pre-apple-", ".sqlite")
        try {
            val google = WeeklyStore(path.toString()).use { store ->
                store.loginGoogle(VerifiedPlayPlayer("google-current", "Google", "google-legacy"), f.now)
            }
            // Recreate the pre-Apple schema while retaining its existing Google data.
            DriverManager.getConnection("jdbc:sqlite:$path").use { connection -> connection.createStatement().use {
                it.execute("DROP TABLE apple_identities"); it.execute("DROP TABLE apple_auth_proofs")
            } }
            WeeklyStore(path.toString()).use { store ->
                val id = google.getString("id")
                assertEquals(id, store.authenticate(google.getString("token"), f.now + 1))
                assertEquals(id, store.loginGoogle(VerifiedPlayPlayer("google-legacy", "Google"), f.now + 2).getString("id"))
                val apple = store.loginGameCenter(f.verifier().verify(f.proof(), f.now), f.now + 3)
                store.deleteAccount(id)
                assertEquals(401, assertFailsWith<ApiProblem> { store.authenticate(google.getString("token"), f.now + 4) }.status)
                assertEquals(apple.getString("id"), store.authenticate(apple.getString("token"), f.now + 4))
                assertNotEquals(id, store.loginGoogle(VerifiedPlayPlayer("google-legacy", "Google"), f.now + 5).getString("id"))
            }
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun failedAccountCreationRollsBackProofConsumption() {
        val path = Files.createTempFile("apple-rollback-", ".sqlite")
        try {
            WeeklyStore(path.toString()).use { store ->
                DriverManager.getConnection("jdbc:sqlite:$path").use { connection -> connection.createStatement().use {
                    it.execute("CREATE TRIGGER reject_player BEFORE INSERT ON players BEGIN SELECT RAISE(ABORT,'fixture'); END")
                } }
                val identity = f.verifier().verify(f.proof(), f.now)
                assertFailsWith<java.sql.SQLException> { store.loginGameCenter(identity, f.now) }
                DriverManager.getConnection("jdbc:sqlite:$path").use { connection -> connection.createStatement().use {
                    it.execute("DROP TRIGGER reject_player")
                } }
                assertEquals("game_center", store.loginGameCenter(identity, f.now).getString("provider"))
            }
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun unsignedNameUrlAndEquivalentEncodingCannotBypassReplay() = WeeklyStore(":memory:").use { store ->
        val proof = f.proof()
        store.loginGameCenter(f.verifier().verify(proof, f.now), f.now)
        proof.put("displayName", "Different unsigned name")
            .put("publicKeyURL", "https://gc.apple.com/public-key/gc-prod-1.cer")
            .put("timestamp", f.now)
            .put("salt", proof.getString("salt").trimEnd('='))
        assertEquals(409, assertFailsWith<ApiProblem> {
            store.loginGameCenter(f.verifier().verify(proof, f.now), f.now)
        }.status)
    }
}
