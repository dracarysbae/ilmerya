package com.bloxtrix.hexdrop.ads

import androidx.compose.runtime.compositionLocalOf

/** Native interstitials at completed-run boundaries. Every show attempt must finish. */
interface AdsManager {
    val isAdReady: Boolean
    val isPrivacyOptionsRequired: Boolean
    fun loadAd()
    fun showInterstitial(onFinished: () -> Unit)
    fun showPrivacyOptions(onFinished: () -> Unit)
}

/** Non-blocking fallback for previews and hosts without native advertising. */
object NoOpAdsManager : AdsManager {
    override val isAdReady = false
    override val isPrivacyOptionsRequired = false
    override fun loadAd() {}
    override fun showInterstitial(onFinished: () -> Unit) = onFinished()
    override fun showPrivacyOptions(onFinished: () -> Unit) = onFinished()
}

val LocalAdsManager = compositionLocalOf<AdsManager> { NoOpAdsManager }
