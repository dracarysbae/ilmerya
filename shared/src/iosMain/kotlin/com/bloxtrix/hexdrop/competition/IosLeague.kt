@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.bloxtrix.hexdrop.competition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implemented in Swift with GameKit. [completion] receives the JSON body for
 * /v1/auth/game-center (teamPlayerID, bundleID, timestamp, salt, signature, publicKeyURL,
 * displayName), or null when the player is not signed in or declined.
 */
interface GameCenterBridge {
    fun authenticate(interactive: Boolean, completion: (String?) -> Unit)
}

internal class IosLeagueTransport(baseUrl: String) : LeagueTransport {
    private val base = baseUrl.trimEnd('/')
    override suspend fun send(method: String, path: String, body: String?, token: String?): LeagueResponse =
        suspendCancellableCoroutine { continuation ->
            require(base.startsWith("https://")) { "HTTPS required" }
            val url = NSURL.URLWithString(base + path) ?: run {
                continuation.resumeWithException(IllegalArgumentException("Invalid URL")); return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(url).apply {
                setHTTPMethod(method)
                // A free host can take more than 50 seconds to wake.
                setTimeoutInterval(70.0)
                setValue("application/json", forHTTPHeaderField = "Accept")
                if (!token.isNullOrBlank()) setValue("Bearer $token", forHTTPHeaderField = "Authorization")
                if (body != null) {
                    setValue("application/json", forHTTPHeaderField = "Content-Type")
                    setHTTPBody(NSString.create(string = body).dataUsingEncoding(NSUTF8StringEncoding))
                }
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (!continuation.isActive) return@dataTaskWithRequest
                val http = response as? NSHTTPURLResponse
                if (error != null || http == null) {
                    continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "No response"))
                } else {
                    val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()
                    continuation.resume(LeagueResponse(http.statusCode.toInt(), text))
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
}

internal class IosLeagueStorage : LeagueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey("ilmerya.league.$key")
    override fun put(key: String, value: String?, commit: Boolean) {
        if (value == null) defaults.removeObjectForKey("ilmerya.league.$key") else defaults.setObject(value, forKey = "ilmerya.league.$key")
        if (commit) defaults.synchronize()
    }
}

internal class GameCenterLeagueIdentity(private val bridge: GameCenterBridge) : LeagueIdentity {
    override val provider = "game_center"
    override val authPath = "/v1/auth/game-center"
    override suspend fun proof(interactive: Boolean): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            bridge.authenticate(interactive) { proof -> if (continuation.isActive) continuation.resume(proof) }
        }
    }
}

fun createIosLeague(apiUrl: String, bridge: GameCenterBridge?): LeagueClient =
    if (bridge == null || !apiUrl.startsWith("https://")) UnavailableLeague
    else LeagueRepository(IosLeagueTransport(apiUrl), IosLeagueStorage(), GameCenterLeagueIdentity(bridge), configured = true)
