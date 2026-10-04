package com.ilmerya.server

import com.bloxtrix.hexdrop.competition.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.*

/** Real HTTP requests against a started service with a temporary SQLite database. */
class ApiIntegrationTest {
    private val client = HttpClient.newHttpClient()

    private fun <T> withServer(guests: Boolean, block: (String) -> T): T {
        val dir = Files.createTempDirectory("ilmerya-api")
        val running = startServer(mapOf("ILMERYA_DB" to dir.resolve("league.sqlite").toString(), "PORT" to "0",
            "ALLOW_GUEST_ACCOUNTS" to guests.toString()))
        try { return block("http://127.0.0.1:${running.port}") } finally { running.close() }
    }
    private fun get(url: String, token: String? = null): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI(url)).apply { token?.let { header("Authorization", "Bearer $it") } }.GET().build(),
        HttpResponse.BodyHandlers.ofString())
    private fun post(url: String, body: JSONObject, token: String? = null): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI(url)).header("Content-Type", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString())

    @Test fun publicPagesHealthAndClosedGuestAccess() = withServer(guests = false) { base ->
        val health = get("$base/health")
        assertEquals(200, health.statusCode())
        assertEquals("ilmerya", JSONObject(health.body()).getString("game"))
        assertEquals(RULESET_VERSION, JSONObject(health.body()).getInt("ruleset"))
        val ads = get("$base/app-ads.txt")
        assertEquals(200, ads.statusCode())
        assertTrue(ads.headers().firstValue("Content-Type").get().startsWith("text/plain"))
        assertEquals("google.com, pub-1875904677314834, DIRECT, f08c47fec0942fa0", ads.body().trim())
        for (page in listOf("/", "/privacy", "/delete-account", "/support")) {
            val response = get(base + page)
            assertEquals(200, response.statusCode(), page)
            assertTrue(response.body().contains("lmerya", ignoreCase = true), page)
        }
        assertTrue(get("$base/privacy").body().contains("Lig hesabımı sil"))
        assertEquals(401, get("$base/v1/profile").statusCode())
        assertEquals(403, post("$base/v1/guest", JSONObject()).statusCode())
        assertEquals(503, post("$base/v1/auth/play-games", JSONObject().put("code", "x".repeat(20))).statusCode())
        assertEquals(503, post("$base/v1/auth/game-center", JSONObject().put("teamPlayerID", "T:1")
            .put("bundleID", "com.ozgames.ilmerya").put("timestamp", "1")).statusCode())
    }

    @Test fun rankedRunTravelsThroughHttpAndIsReplayedOnTheServer() = withServer(guests = true) { base ->
        val player = JSONObject(post("$base/v1/guest", JSONObject()).body())
        val token = player.getString("token")
        assertEquals(200, get("$base/v1/profile", token).statusCode())
        val ticket = JSONObject(post("$base/v1/runs", JSONObject().put("ruleset", RULESET_VERSION), token).body())
        val seed = ticket.getString("seed").toLong()
        val bag = StoneBag(Random(seed)); var state = RunReplay.initial(bag)
        val events = JSONArray(); var count = 0
        while (state.phase == GamePhase.Playing) {
            val col = (0 until COLS).firstOrNull { !state.grid.isColumnFull(it) }
            val action = if (col != null) "D$col" else "C${(0 until COLS).first { cycleColumn(state.grid, it) != null }}"
            events.put(JSONArray().put(count * 450L).put(action)); count++
            state = RunReplay.apply(state, bag, action)
        }
        val finish = "$base/v1/runs/${ticket.getString("id")}/finish"
        val body = JSONObject().put("ruleset", RULESET_VERSION).put("events", events)
        // The moves claim more time than has passed since the ticket: rejected as fast-forwarded.
        assertEquals(400, post(finish, body, token).statusCode())
        Thread.sleep(count * 450L + 300)
        val accepted = post(finish, body, token)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(state.score, JSONObject(accepted.body()).getLong("score"))
        assertTrue(JSONObject(post(finish, body, token).body()).getBoolean("duplicate"))
        val me = JSONObject(get("$base/v1/leaderboard", token).body()).getJSONObject("me")
        assertEquals(1, me.getInt("rank")); assertEquals(state.score, me.getLong("score"))
        assertEquals(400, post("$base/v1/runs", JSONObject().put("ruleset", RULESET_VERSION + 1), token).statusCode())
        val board = JSONObject(get("$base/v1/leaderboard", token).body())
        assertEquals(1, board.getJSONArray("entries").length())
        assertEquals("Europe/Istanbul", board.getString("timeZone"))
        assertEquals(200, post("$base/v1/account/delete", JSONObject().put("confirm", true), token).statusCode())
        assertEquals(401, get("$base/v1/profile", token).statusCode())
    }
}
