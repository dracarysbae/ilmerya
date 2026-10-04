package com.ilmerya.server

import kotlin.test.*

class LeagueDatabaseTest {
    @Test fun cloudDatabaseUsesContainerTrustStoreWithoutDisablingHostnameChecks() {
        val result = verifiedPostgresUrl("jdbc:postgresql://example.neon.tech/league?sslmode=verify-full")
        assertTrue(result.contains("sslmode=verify-full"))
        assertTrue(result.endsWith("sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"))
        assertEquals(result, verifiedPostgresUrl(result))
    }

    @Test fun remoteDatabaseRejectsCustomTrustAndHostnameBypasses() {
        for (suffix in listOf("&sslfactory=org.postgresql.ssl.NonValidatingFactory",
            "&sslhostnameverifier=example.AllowAll")) {
            assertFailsWith<IllegalArgumentException> {
                verifiedPostgresUrl("jdbc:postgresql://example.neon.tech/league?sslmode=verify-full$suffix")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            verifiedPostgresUrl("jdbc:postgresql://localhost:5432,remote.example:5432/league?sslmode=disable")
        }
    }

    @Test fun remoteDatabaseCannotDisableCertificateVerification() {
        for (url in listOf(
            "jdbc:postgresql://db.example.invalid/league?sslmode=disable",
            "jdbc:postgresql://db.example.invalid/league?sslmode=allow",
            "jdbc:postgresql://db.example.invalid/league?sslmode=prefer"
        )) assertFailsWith<IllegalArgumentException> { LeagueDatabase(":memory:", url) }
    }

    @Test fun encryptionOnlyProviderStringsAreUpgradedToFullVerification() {
        for (url in listOf(
            "jdbc:postgresql://ep-x.eu-central-1.aws.neon.tech/neondb?user=u&password=p&sslmode=require&channelBinding=require",
            "jdbc:postgresql://ep-x.eu-central-1.aws.neon.tech/neondb?user=u&password=p",
        )) {
            val result = verifiedPostgresUrl(url)
            assertTrue("sslmode=verify-full" in result, result)
            assertFalse("sslmode=require" in result, result)
            assertTrue(result.endsWith("sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"))
        }
    }

    @Test fun transactionFailureRollsBackAndConnectionCanBeReused() {
        LeagueDatabase(":memory:", null).use { database ->
            database.connection { it.createStatement().use { s -> s.execute("CREATE TABLE probe(value TEXT)") } }
            assertFailsWith<IllegalStateException> {
                database.transaction {
                    database.connection { it.createStatement().use { s -> s.execute("INSERT INTO probe VALUES('incomplete')") } }
                    error("interrupt")
                }
            }
            database.transaction {
                database.connection { it.createStatement().use { s -> s.execute("INSERT INTO probe VALUES('committed')") } }
            }
            database.connection { it.createStatement().use { s ->
                s.executeQuery("SELECT value FROM probe").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("committed", rows.getString(1))
                    assertFalse(rows.next())
                }
            } }
        }
    }
}
