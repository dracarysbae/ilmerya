package com.ilmerya.server

import java.time.Instant
import java.nio.file.Files
import kotlin.test.*

class WeeklyStoreTest {
    private val sunday=Instant.parse("2026-09-13T20:59:59Z").toEpochMilli()
    private fun player(s: WeeklyStore,now: Long)=s.register(now).getString("id")
    private fun score(s: WeeklyStore,p: String,score: Long,now: Long): String {
        val run=s.startRun(p,now-100).getString("id")
        s.submit(run,p,"hash-$run",score,now)
        return run
    }
    @Test fun istanbulMondayBoundaryAndYearRollover() {
        assertEquals("2026-09-07",weekAt(sunday).id)
        assertEquals(sunday+1000,weekAt(sunday).end)
        assertEquals("2026-09-14",weekAt(sunday+1000).id)
        assertEquals("2025-12-29",weekAt(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()).id)
    }
    @Test fun rankingUsesBestScoreThenEarliestAchievement() = WeeklyStore(":memory:").use { s ->
        val a=player(s,sunday-5000); val b=player(s,sunday-5000)
        score(s,a,1000,sunday-3000); score(s,b,1000,sunday-2000); score(s,a,100,sunday-1000)
        val entries=s.leaderboard(a,sunday).getJSONArray("entries")
        assertEquals(a,entries.getJSONObject(0).getString("playerId"))
        assertEquals(1000,entries.getJSONObject(0).getLong("score"))
        assertEquals(1,s.leaderboard(a,sunday).getJSONObject("me").getInt("rank"))
    }
    @Test fun closeSettlesExactlyOnceAndPersistsAcrossRestart() {
        val path=Files.createTempFile("weekly-", ".sqlite")
        val players=WeeklyStore(path.toString()).use { s ->
            val players=List(4) { player(s,sunday-5000) }
            players.forEachIndexed { i,p -> score(s,p,400L-i*100,sunday-1000+i) }
            s.closeExpired(sunday+1000); s.closeExpired(sunday+2000)
            players
        }
        WeeklyStore(path.toString()).use { s ->
            s.closeExpired(sunday+3000)
            players.forEachIndexed { i,p ->
                val history=s.profile(p,sunday+3000).getJSONArray("history")
                assertEquals(1,history.length())
                assertEquals(i+1,history.getJSONObject(0).getInt("rank"))
                assertEquals(400L-i*100,history.getJSONObject(0).getLong("score"))
            }
            assertFalse(s.profile(players[0],sunday+3000).has("owned"))
            val board=s.leaderboard(players[0],sunday+3000,true)
            assertTrue(board.getBoolean("final")); assertEquals(4,board.getJSONArray("entries").length())
            assertEquals(0,s.leaderboard(players[0],sunday+3000).getJSONArray("entries").length())
        }
        Files.deleteIfExists(path)
    }
    @Test fun submissionIsIdempotentAndLateRunsCannotChangeResults() = WeeklyStore(":memory:").use { s ->
        val p=player(s,sunday-5000)
        val run=score(s,p,500,sunday-3000)
        assertTrue(s.submit(run,p,"hash-$run",99999,sunday).getBoolean("duplicate"))
        assertEquals(409,assertFailsWith<ApiProblem> { s.submit(run,p,"changed",99999,sunday) }.status)
        val late=s.startRun(p,sunday).getString("id")
        assertEquals(409,assertFailsWith<ApiProblem> { s.submit(late,p,"late",99999,sunday+1000) }.status)
        assertEquals(500,s.leaderboard(p,sunday+1000,true).getJSONObject("me").getLong("score"))
    }
    @Test fun tokensOwnershipAndReplacementAreEnforced() = WeeklyStore(":memory:").use { s ->
        val profile=s.register(sunday)
        val p=profile.getString("id")
        assertEquals(p,s.authenticate(profile.getString("token")))
        assertEquals(401,assertFailsWith<ApiProblem> { s.authenticate("invalid") }.status)
        val first=s.startRun(p,sunday-100).getString("id")
        s.startRun(p,sunday)
        assertEquals(409,assertFailsWith<ApiProblem> { s.ticket(first,p) }.status)
        assertEquals(404,assertFailsWith<ApiProblem> { s.ticket(first,player(s,sunday)) }.status)
    }
    @Test fun everyRankedPlayerKeepsAFinalResultWithoutRewards() = WeeklyStore(":memory:").use { s ->
        val players=List(11) { player(s,sunday-5000) }
        players.forEachIndexed { i,p -> score(s,p,1100L-i*100,sunday-1000+i) }
        s.closeExpired(sunday+1000)
        players.forEachIndexed { i,p ->
            assertEquals(i+1,s.profile(p,sunday+1000).getJSONArray("history").getJSONObject(0).getInt("rank"))
        }
        assertEquals(11,s.leaderboard(players[0],sunday+1000,true).getJSONArray("entries").length())
    }
    @Test fun deletingAccountRevokesEveryDeviceAndRemovesItsHistory() = WeeklyStore(":memory:").use { s ->
        val identity=VerifiedPlayPlayer("to-delete","Test player")
        val first=s.loginGoogle(identity,sunday-5000)
        val id=first.getString("id")
        val second=s.loginGoogle(identity,sunday-4000)
        val other=player(s,sunday-4000)
        score(s,id,900,sunday-1000); score(s,other,800,sunday-900)
        s.closeExpired(sunday+1000)
        s.startRun(id,sunday+2000)
        assertTrue(s.deleteAccount(id).getBoolean("deleted"))
        for(p in listOf(first,second)) assertEquals(401,assertFailsWith<ApiProblem> {s.authenticate(p.getString("token"),sunday+3000)}.status)
        val remaining=s.leaderboard(other,sunday+3000,true).getJSONArray("entries")
        assertEquals(1,remaining.length()); assertEquals(2,remaining.getJSONObject(0).getInt("rank"))
        assertEquals(1,s.profile(other,sunday+3000).getJSONArray("history").length())
        val returned=s.loginGoogle(identity,sunday+4000)
        assertNotEquals(id,returned.getString("id"))
        assertEquals(0,returned.getJSONArray("history").length())
    }
    @Test fun googleAccountRestoresHistoryAndKeepsIndependentDeviceSessions() = WeeklyStore(":memory:").use { s ->
        val first=s.loginGoogle(VerifiedPlayPlayer("google-player","Ada"),sunday-5000)
        val id=first.getString("id")
        score(s,id,800,sunday-1000)
        s.closeExpired(sunday+1000)
        val second=s.loginGoogle(VerifiedPlayPlayer("google-player","Ada renamed"),sunday+2000)
        assertEquals(id,second.getString("id")); assertEquals("Ada renamed",second.getString("name"))
        assertEquals(1,second.getJSONArray("history").getJSONObject(0).getInt("rank"))
        assertEquals("play_games",second.getString("provider"))
        assertEquals(id,s.authenticate(first.getString("token"),sunday+3000))
        assertEquals(id,s.authenticate(second.getString("token"),sunday+3000))
        assertEquals(401,assertFailsWith<ApiProblem> { s.authenticate(first.getString("token"),sunday+31L*86400000) }.status)
        assertNotEquals(id,s.loginGoogle(VerifiedPlayPlayer("other-google","Ada renamed"),sunday).getString("id"))
        assertEquals(id,s.loginGoogle(VerifiedPlayPlayer("new-player-id","Ada","google-player"),sunday+3000).getString("id"))
    }
}
