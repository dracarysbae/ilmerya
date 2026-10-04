package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.competition.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class MapStorage : LeagueStorage {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String?, commit: Boolean) { if (value == null) values.remove(key) else values[key] = value }
}

private class FakeIdentity(var signedIn: Boolean = true) : LeagueIdentity {
    override val provider = "play_games"
    override val authPath = "/v1/auth/play-games"
    var interactiveCalls = 0; var silentCalls = 0
    override suspend fun proof(interactive: Boolean): String? {
        if (interactive) interactiveCalls++ else silentCalls++
        return if (signedIn || interactive) """{"code":"one-use-code-123"}""" else null
    }
}

/** A minimal in-memory league endpoint with the same status codes as the server. */
private class FakeServer : LeagueTransport {
    var online = true
    var validToken = "token-1"
    var tokens = 0
    var finishStatus = 200
    val finished = mutableListOf<String>()
    val requests = mutableListOf<String>()
    override suspend fun send(method: String, path: String, body: String?, token: String?): LeagueResponse {
        requests += "$method $path"
        if (!online) throw IllegalStateException("offline")
        if (path == "/health") return LeagueResponse(200, """{"ok":true,"game":"ilmerya","ruleset":$RULESET_VERSION}""")
        if (path == "/v1/auth/play-games") { validToken = "token-${++tokens}"; return LeagueResponse(200, """{"id":"p1","name":"Ada","provider":"play_games","history":[],"token":"$validToken"}""") }
        if (token != validToken) return LeagueResponse(401, """{"error":"Session expired"}""")
        return when {
            path == "/v1/profile" -> LeagueResponse(200, """{"id":"p1","name":"Ada","provider":"play_games","history":[{"week":"2026-09-21","rank":2,"score":900}]}""")
            path.startsWith("/v1/leaderboard") -> LeagueResponse(200, """{"week":"2026-09-28","endsAt":5000,"serverNow":1000,"entries":[{"playerId":"p1","name":"Ada","rank":1,"score":700}],"me":{"playerId":"p1","name":"Ada","rank":1,"score":700}}""")
            path == "/v1/runs" -> LeagueResponse(200, """{"id":"00000000-0000-0000-0000-00000000000a","seed":"42","week":"2026-09-28","startsAt":1000,"endsAt":5000}""")
            path.endsWith("/finish") -> { if (finishStatus == 200) finished += path; LeagueResponse(finishStatus, "{}") }
            path == "/v1/account/delete" -> LeagueResponse(200, """{"deleted":true}""")
            else -> LeagueResponse(404, "{}")
        }
    }
}

class LeagueRepositoryTest {
    private val run = CompletedRun(42, listOf(RunEvent(0, "D0")))

    @Test fun neverJoinedPlayerIsNotSignedInSilently() = runTest {
        val identity = FakeIdentity(signedIn = true)
        val repo = LeagueRepository(FakeServer(), MapStorage(), identity, configured = true)
        assertTrue(repo.state.value.loginRequired)
        assertFalse(repo.signInSilently())
        repo.refresh()
        assertEquals(0, identity.silentCalls)
        assertTrue(repo.state.value.loginRequired)
    }

    @Test fun joiningKeepsTheSessionAcrossLaunchesAndRenewsAnExpiredToken() = runTest {
        val server = FakeServer(); val storage = MapStorage(); val identity = FakeIdentity()
        val first = LeagueRepository(server, storage, identity, configured = true)
        first.signIn()
        assertFalse(first.state.value.loginRequired)
        assertEquals(1, first.state.value.current!!.me!!.rank)
        assertEquals(listOf(WeeklyFinish("2026-09-21", 2, 900)), first.state.value.profile.history)
        // Next launch: the stored session is used without any platform sign-in.
        val second = LeagueRepository(server, storage, identity, configured = true)
        assertFalse(second.state.value.loginRequired)
        second.refresh()
        assertEquals(1, identity.interactiveCalls); assertEquals(0, identity.silentCalls)
        // The server forgets the session: one silent renewal, no UI.
        server.validToken = "rotated"
        second.refresh()
        assertEquals(1, identity.silentCalls)
        assertTrue(second.state.value.connected)
        assertFalse(second.state.value.loginRequired)
    }

    @Test fun offlineResultsAreQueuedBoundedAndBlockNewTickets() = runTest {
        val server = FakeServer(); val storage = MapStorage()
        val repo = LeagueRepository(server, storage, FakeIdentity(), configured = true)
        repo.signIn()
        server.online = false
        repeat(4) { i ->
            val ticket = RankedTicket("00000000-0000-0000-0000-00000000000$i", 42, "w", 5000, "p1", 1000)
            assertFails { repo.submit(ticket, run) }
        }
        assertEquals(3, Regex("\"id\"").findAll(storage.get("pending_runs")!!).count())
        assertNull(repo.startRun())
        server.online = true
        repo.refresh()
        assertEquals(3, server.finished.size)
        assertNotNull(repo.startRun())
    }

    @Test fun closedWeekRejectsTheResultInsteadOfRetryingForever() = runTest {
        val server = FakeServer(); val storage = MapStorage()
        val repo = LeagueRepository(server, storage, FakeIdentity(), configured = true)
        repo.signIn()
        server.finishStatus = 409
        val failure = assertFailsWith<LeagueFailure> { repo.submit(RankedTicket("00000000-0000-0000-0000-00000000000b", 42, "w", 5000, "p1", 1000), run) }
        assertEquals("closed", failure.reason)
        assertEquals("[]", storage.get("pending_runs"))
    }

    @Test fun deletingTheAccountClearsLocalDataAndStopsAutomaticSignIn() = runTest {
        val server = FakeServer(); val storage = MapStorage(); val identity = FakeIdentity()
        val repo = LeagueRepository(server, storage, identity, configured = true)
        repo.signIn()
        assertTrue(repo.deleteAccount())
        assertTrue(repo.state.value.loginRequired)
        assertNull(storage.get("token")); assertNull(storage.get("profile"))
        assertFalse(LeagueRepository(server, storage, identity, configured = true).signInSilently())
    }

    @Test fun incompatibleServerIsRefusedBeforeTheAuthCodeIsSpent() = runTest {
        val server = object : LeagueTransport {
            val paths = mutableListOf<String>()
            override suspend fun send(method: String, path: String, body: String?, token: String?): LeagueResponse {
                paths += path
                return LeagueResponse(200, """{"ok":true,"game":"bloxboom","ruleset":2}""")
            }
        }
        val repo = LeagueRepository(server, MapStorage(), FakeIdentity(), configured = true)
        repo.signIn()
        assertEquals(listOf("/health"), server.paths)
        assertEquals("update", repo.state.value.error)
        assertTrue(repo.state.value.loginRequired)
    }
}
