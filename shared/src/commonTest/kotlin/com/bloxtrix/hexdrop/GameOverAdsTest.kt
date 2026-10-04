package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.ads.*
import com.bloxtrix.hexdrop.model.*
import kotlin.test.*

class GameOverAdsTest {
    private class FakeAds : AdsManager {
        override val isAdReady = true
        override val isPrivacyOptionsRequired = false
        var shows = 0
        var finish: (() -> Unit)? = null
        override fun loadAd() {}
        override fun showInterstitial(onFinished: () -> Unit) { shows++; finish = onFinished }
        override fun showPrivacyOptions(onFinished: () -> Unit) = onFinished()
    }
    @Test fun finishedRunShowsOnlyOnceAcrossRepeatedScreenEvents() {
        val ads = FakeAds(); val gate = GameOverAdGate()
        val over = GameState(runId = 1, phase = GamePhase.GameOver)
        repeat(8) { gate.showOnce(over, ads) }
        assertEquals(1, ads.shows); assertTrue(gate.pending.value)
        assertEquals(-1L, gate.completedRun.value)
        ads.finish!!(); ads.finish!!()
        assertFalse(gate.pending.value)
        assertEquals(1L, gate.completedRun.value)
        gate.showOnce(over, ads)
        assertEquals(1, ads.shows)
    }
    @Test fun pausePlayAndUnfinishedAnimationNeverShowAnAd() {
        val ads = FakeAds(); val gate = GameOverAdGate()
        gate.showOnce(GameState(phase = GamePhase.Paused), ads)
        gate.showOnce(GameState(phase = GamePhase.Playing), ads)
        gate.showOnce(GameState(phase = GamePhase.GameOver, resolving = true), ads)
        assertEquals(0, ads.shows); assertFalse(gate.pending.value)
    }
    @Test fun everyNewCompletedRunGetsAnAttemptAndStaleCallbackCannotUnlockIt() {
        val ads = FakeAds(); val gate = GameOverAdGate()
        gate.showOnce(GameState(runId = 1, phase = GamePhase.GameOver), ads)
        val oldCallback = ads.finish!!
        gate.beginRun()
        gate.showOnce(GameState(runId = 2, phase = GamePhase.GameOver), ads)
        assertEquals(2, ads.shows); assertTrue(gate.pending.value)
        // A duplicate callback from a previous presentation cannot release the new one.
        oldCallback()
        assertTrue(gate.pending.value)
        assertEquals(-1L, gate.completedRun.value)
        ads.finish!!()
        assertFalse(gate.pending.value)
    }
    @Test fun unavailableAdsImmediatelyReleaseResultsWithoutRetryingThatRun() {
        val gate = GameOverAdGate()
        val over = GameState(runId = 9, phase = GamePhase.GameOver)
        gate.showOnce(over, NoOpAdsManager)
        assertFalse(gate.pending.value)
        assertEquals(9L, gate.completedRun.value)
        val ads = FakeAds()
        gate.showOnce(over, ads)
        assertEquals(0, ads.shows)
    }
}
