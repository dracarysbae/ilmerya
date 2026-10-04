package com.bloxtrix.hexdrop.competition

import android.app.Activity
import com.bloxtrix.hexdrop.BuildConfig
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitGames(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if(continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

internal object PlayGamesIdentity {
    suspend fun code(activity: Activity,interactive: Boolean): String? {
        if(!BuildConfig.PGS_CONFIGURED || activity.isDestroyed) return null
        val client=PlayGames.getGamesSignInClient(activity)
        val authenticated=if(interactive) client.signIn().awaitGames().isAuthenticated
            else client.isAuthenticated.awaitGames().isAuthenticated
        if(!authenticated || activity.isDestroyed) return null
        // Only an authorization code crosses to our server. No email scope is requested.
        return client.requestServerSideAccess(BuildConfig.PGS_WEB_CLIENT_ID,false).awaitGames()
    }
}
