package com.bloxtrix.hexdrop.competition

import android.app.Activity
import android.content.Context
import com.bloxtrix.hexdrop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URI

/** HTTPS only; plain HTTP is accepted for a local debug server. */
internal class AndroidLeagueTransport(baseUrl: String) : LeagueTransport {
    private val base = baseUrl.trimEnd('/')
    override suspend fun send(method: String, path: String, body: String?, token: String?): LeagueResponse = withContext(Dispatchers.IO) {
        val uri = URI(base)
        val local = BuildConfig.DEBUG && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
        require(uri.scheme == "https" || (local && uri.scheme == "http")) { "HTTPS required" }
        val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
        try {
            // A free host can take more than 50 seconds to wake; one-use Google codes are never retried.
            connection.connectTimeout = 10_000; connection.readTimeout = 70_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            LeagueResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally { connection.disconnect() }
    }
}

internal class AndroidLeagueStorage(context: Context) : LeagueStorage {
    private val prefs = context.getSharedPreferences("ilmerya_competition", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?, commit: Boolean) {
        val editor = prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }
        if (commit) editor.commit() else editor.apply()
    }
}

/** Google Play Games v2. The activity is bound while it is resumed; no email scope is requested. */
internal class PlayGamesLeagueIdentity : LeagueIdentity {
    @Volatile private var activity = WeakReference<Activity>(null)
    override val provider = "play_games"
    override val authPath = "/v1/auth/play-games"
    fun bind(host: Activity) { activity = WeakReference(host) }
    fun unbind(host: Activity) { if (activity.get() === host) activity.clear() }
    override suspend fun proof(interactive: Boolean): String? = withContext(Dispatchers.Main) {
        val host = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return@withContext null
        PlayGamesIdentity.code(host, interactive)?.let { JSONObject().put("code", it).toString() }
    }
}

internal fun createAndroidLeague(context: Context): Pair<LeagueRepository, PlayGamesLeagueIdentity> {
    val identity = PlayGamesLeagueIdentity()
    val repository = LeagueRepository(AndroidLeagueTransport(BuildConfig.COMPETITION_URL), AndroidLeagueStorage(context), identity,
        configured = BuildConfig.PGS_CONFIGURED && BuildConfig.COMPETITION_URL.isNotBlank())
    return repository to identity
}