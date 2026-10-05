package com.bloxtrix.hexdrop.competition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** HTTP status and body. Implementations throw on a transport failure (offline, timeout, TLS). */
data class LeagueResponse(val status: Int, val body: String)

interface LeagueTransport {
    /** [path] starts with '/'. Redirects must not be followed; [token] is sent as a Bearer header. */
    suspend fun send(method: String, path: String, body: String?, token: String?): LeagueResponse
}

/** Private app storage; [commit] must reach disk before it returns. */
interface LeagueStorage {
    fun get(key: String): String?
    fun put(key: String, value: String?, commit: Boolean = false)
}

/** Platform game identity: returns the JSON body for [authPath], or null when the player is not signed in. */
interface LeagueIdentity {
    val provider: String
    val authPath: String
    suspend fun proof(interactive: Boolean): String?
}

private class LeagueHttpProblem(val code: Int) : Exception("HTTP $code")

/**
 * Shared league client for Android (Google Play Games) and iOS (Game Center).
 * The server replays every ranked run; this client never reports a score of its own.
 */
class LeagueRepository(
    private val transport: LeagueTransport,
    private val storage: LeagueStorage,
    private val identity: LeagueIdentity,
    override val configured: Boolean,
) : LeagueClient {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    override val provider: String get() = identity.provider
    private val _state = MutableStateFlow(WeeklyState(
        profile = runCatching { profile(json.parseToJsonElement(storage.get(PROFILE) ?: "{}").jsonObject) }.getOrDefault(PlayerProfile()),
        loginRequired = token().isBlank()))
    override val state: StateFlow<WeeklyState> = _state.asStateFlow()

    /** Only a player who chose to join is signed in silently; deleting the account ends that. */
    val autoSignInAllowed: Boolean get() = storage.get(JOINED) == "true"

    private fun token() = storage.get(TOKEN).orEmpty()
    private fun JsonObject.text(key: String, fallback: String = "") = (this[key] as? JsonPrimitive)?.contentOrNull ?: fallback
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun profile(value: JsonObject) = PlayerProfile(value.text("id"), value.text("name"),
        (value["history"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
            WeeklyFinish(it.text("week"), it.long("rank").toInt(), it.long("score"))
        }, value.text("provider"))
    private fun rank(value: JsonObject) = RankEntry(value.text("playerId"), value.text("name"), value.long("rank").toInt(), value.long("score"))
    private fun board(value: JsonObject) = WeeklyBoard(value.text("week"), value.long("endsAt"), value.long("serverNow"),
        (value["entries"] as? JsonArray).orEmpty().map { rank(it.jsonObject) }, (value["me"] as? JsonObject)?.let(::rank))
    private fun cacheProfile(value: JsonObject) {
        val withoutToken = JsonObject(value - "token")
        storage.put(PROFILE, withoutToken.toString())
        _state.update { it.copy(profile = profile(withoutToken)) }
    }

    private suspend fun api(method: String, path: String, body: JsonObject? = null, anonymous: Boolean = false): JsonObject {
        val response = transport.send(method, path, body?.toString(), if (anonymous) null else token())
        if (response.status !in 200..299) throw LeagueHttpProblem(response.status)
        return json.parseToJsonElement(response.body).jsonObject
    }
    private fun errorCode(e: Exception) = when ((e as? LeagueHttpProblem)?.code) {
        409 -> "closed"; 401, 403 -> "session"; 429 -> "busy"; 426 -> "update"; else -> "offline"
    }
    private fun ensureSession() { if (_state.value.loginRequired || token().isBlank()) throw LeagueHttpProblem(401) }

    private suspend fun authenticate(proof: String) {
        val health = api("GET", "/health", anonymous = true)
        if (health.text("game") != "ilmerya" || health.long("ruleset").toInt() != RULESET_VERSION) throw LeagueHttpProblem(426)
        val value = api("POST", identity.authPath, json.parseToJsonElement(proof).jsonObject, anonymous = true)
        val previous = _state.value.profile.id
        val changed = previous.isNotBlank() && previous != value.text("id")
        storage.put(TOKEN, value.text("token"))
        storage.put(JOINED, "true")
        // A different account on this device must not inherit another player's unsent runs.
        if (changed) storage.put(PENDING, null)
        storage.put(TOKEN, value.text("token"), commit = true)
        cacheProfile(value)
        _state.update { it.copy(signingIn = false, loginRequired = false, connected = true, error = "",
            current = if (changed) null else it.current, previous = if (changed) null else it.previous) }
    }

    /** Silent sign-in at launch or after an expired session; never shows platform UI. */
    suspend fun signInSilently(): Boolean {
        if (!configured || !autoSignInAllowed) return false
        val proof = runCatching { identity.proof(false) }.getOrNull() ?: return false
        return mutex.withLock {
            try { authenticate(proof); true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
        }
    }

    override suspend fun signIn() {
        if (!configured) return
        _state.update { it.copy(signingIn = true, error = "") }
        try {
            val proof = identity.proof(true)
            // Cancelled, or the platform did not authenticate this profile (for example not a tester yet).
            if (proof == null) { _state.update { it.copy(signingIn = false, error = "signin") }; return }
            mutex.withLock { authenticate(proof) }
            refresh()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _state.update { it.copy(signingIn = false, connected = false, error = errorCode(e), loginRequired = token().isBlank()) }
        }
    }

    private suspend fun flushFinishes(): Set<String> {
        val rejected = mutableSetOf<String>()
        val pending = runCatching { json.parseToJsonElement(storage.get(PENDING) ?: "[]").jsonArray }.getOrDefault(JsonArray(emptyList()))
        val keep = mutableListOf<JsonElement>()
        for (item in pending) {
            val entry = item.jsonObject
            val id = entry.text("id")
            try { api("POST", "/v1/runs/$id/finish", entry["body"]!!.jsonObject) }
            catch (e: CancellationException) { throw e }
            catch (e: LeagueHttpProblem) { if (e.code in listOf(400, 404, 409)) rejected += id else keep += item }
            catch (_: Exception) { keep += item }
        }
        storage.put(PENDING, JsonArray(keep).toString(), commit = true)
        return rejected
    }
    private fun pendingCount() = runCatching { json.parseToJsonElement(storage.get(PENDING) ?: "[]").jsonArray.size }.getOrDefault(0)

    private suspend fun expiredSession(e: Exception): Boolean {
        if ((e as? LeagueHttpProblem)?.code != 401 || token().isBlank()) return false
        storage.put(TOKEN, null, commit = true)
        _state.update { it.copy(loginRequired = true) }
        if (!autoSignInAllowed) return false
        val proof = runCatching { identity.proof(false) }.getOrNull() ?: return false
        return runCatching { authenticate(proof) }.isSuccess
    }

    override suspend fun refresh() {
        if (!configured) return
        if (_state.value.loginRequired && !signInSilently()) return
        mutex.withLock {
            _state.update { it.copy(loading = true, error = "") }
            var retried = false
            while (true) {
                try {
                    ensureSession(); flushFinishes()
                    val p = api("GET", "/v1/profile")
                    val current = board(api("GET", "/v1/leaderboard"))
                    val previous = board(api("GET", "/v1/leaderboard?previous=true"))
                    cacheProfile(p)
                    _state.update { it.copy(current = current, previous = previous, loading = false, connected = true, error = "") }
                    return
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (!retried && expiredSession(e)) { retried = true; continue }
                    _state.update { it.copy(loading = false, connected = false, error = errorCode(e),
                        loginRequired = it.loginRequired || token().isBlank()) }
                    return
                }
            }
        }
    }

    override suspend fun startRun(): RankedTicket? {
        if (!configured || _state.value.loginRequired || token().isBlank()) return null
        return mutex.withLock {
            try {
                ensureSession(); flushFinishes()
                // A new ticket cancels the previous unfinished server run; keep queued replays until delivered.
                if (pendingCount() > 0) { _state.update { it.copy(error = "pending") }; return@withLock null }
                val value = api("POST", "/v1/runs", buildJsonObject { put("ruleset", RULESET_VERSION) })
                _state.update { it.copy(connected = true, error = "") }
                RankedTicket(value.text("id"), value.text("seed").toLong(), value.text("week"), value.long("endsAt"),
                    _state.value.profile.id, value.long("startsAt"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.update { it.copy(connected = false, error = errorCode(e),
                    loginRequired = it.loginRequired || (e as? LeagueHttpProblem)?.code in listOf(401, 403)) }
                null
            }
        }
    }

    override suspend fun submit(ticket: RankedTicket, run: CompletedRun) = mutex.withLock {
        if (ticket.seed != run.seed || (ticket.playerId.isNotBlank() && ticket.playerId != _state.value.profile.id))
            throw LeagueFailure("session")
        val body = buildJsonObject {
            put("ruleset", RULESET_VERSION)
            put("events", buildJsonArray { run.events.forEach { e -> add(buildJsonArray { add(e.ms); add(e.action) }) } })
        }
        val existing = runCatching { json.parseToJsonElement(storage.get(PENDING) ?: "[]").jsonArray }.getOrDefault(JsonArray(emptyList()))
            .filter { it.jsonObject.text("id") != ticket.id }
        // At most three unsent results are kept on the device.
        val queue = existing.takeLast(2) + buildJsonObject { put("id", ticket.id); put("body", body) }
        storage.put(PENDING, JsonArray(queue).toString(), commit = true)
        if (_state.value.loginRequired || token().isBlank()) throw LeagueHttpProblem(0)
        val rejected = flushFinishes()
        if (ticket.id in rejected) throw LeagueFailure("closed")
        if (pendingCount() > 0) throw LeagueHttpProblem(0)
    }

    override suspend fun deleteAccount(): Boolean = mutex.withLock {
        _state.update { it.copy(loading = true, error = "") }
        try {
            ensureSession()
            api("POST", "/v1/account/delete", buildJsonObject { put("confirm", true) })
            for (key in listOf(TOKEN, PROFILE, PENDING)) storage.put(key, null)
            storage.put(JOINED, null, commit = true)
            _state.value = WeeklyState()
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            _state.update { it.copy(loading = false, error = "delete_failed") }
            false
        }
    }

    private companion object {
        const val TOKEN = "token"
        const val PROFILE = "profile"
        const val PENDING = "pending_runs"
        const val JOINED = "joined"
    }
}
