package com.bloxtrix.hexdrop.ads

import com.bloxtrix.hexdrop.model.GamePhase
import com.bloxtrix.hexdrop.model.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained with the game, so recomposition and Activity recreation cannot replay an ad. */
class GameOverAdGate {
    private var handledRun = -1L
    private var callbackToken = 0L
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()
    private val _completedRun = MutableStateFlow(-1L)
    val completedRun = _completedRun.asStateFlow()

    fun beginRun() { callbackToken++; _pending.value = false }

    /** Screenshot scenes only: treat [runId] as already finished without an ad. */
    fun markCompleted(runId: Long) { handledRun = runId; _pending.value = false; _completedRun.value = runId }

    fun showOnce(state: GameState, ads: AdsManager) {
        if (state.phase != GamePhase.GameOver || state.resolving || handledRun == state.runId) return
        handledRun = state.runId
        val token = ++callbackToken
        _pending.value = true
        val finished = {
            if (callbackToken == token) {
                _pending.value = false
                _completedRun.value = state.runId
            }
        }
        try { ads.showInterstitial(finished) } catch (_: Exception) { finished() }
    }
}
