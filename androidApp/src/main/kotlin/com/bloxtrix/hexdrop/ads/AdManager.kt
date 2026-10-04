package com.bloxtrix.hexdrop.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bloxtrix.hexdrop.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference

/** One cached game-over interstitial. Retained by GameSession, never by an Activity. */
class AndroidAdsManager(context: Context) : AdsManager {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val consent = UserMessagingPlatform.getConsentInformation(appContext)
    private var activity = WeakReference<Activity>(null)
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var loadToken = 0
    private var sdkStarting = false
    private var sdkReady = false
    private var consentRequested = false
    private var consentFormPending = false
    private var privacyFormShowing = false
    private var presenting = false
    private var disposed = false
    private var finishPresentation: (() -> Unit)? = null
    private var finishPrivacy: (() -> Unit)? = null

    override var isAdReady: Boolean by mutableStateOf(false)
        private set
    override var isPrivacyOptionsRequired: Boolean by mutableStateOf(false)
        private set

    private val expireAd: Runnable = Runnable {
        clearCachedAd()
        if (foregroundActivity() != null) loadAd()
    }
    private val loadTimeout: Runnable = Runnable {
        // An SDK/network request must not permanently suppress future preloads.
        loadToken++
        loading = false
    }

    fun initialize(host: Activity): Unit = onMain {
        if (disposed || consentRequested) return@onMain
        consentRequested = true
        consent.requestConsentInfoUpdate(host, ConsentRequestParameters.Builder().build(), {
            onMain {
                if (!disposed) {
                    updatePrivacyRequirement()
                    consentFormPending = true
                    presentConsentIfPossible()
                }
            }
        }, { error ->
            onMain {
                Log.w(TAG, "Consent update failed: ${error.errorCode}")
                updatePrivacyRequirement()
                initializeSdkIfAllowed()
            }
        })
        // UMP may allow requests using valid consent from the previous launch.
        initializeSdkIfAllowed()
    }

    fun bindActivity(host: Activity): Unit = onMain {
        if (disposed) return@onMain
        activity = WeakReference(host)
        presentConsentIfPossible()
        initializeSdkIfAllowed()
        loadAd()
    }

    fun unbindActivity(host: Activity): Unit = onMain {
        if (activity.get() === host) activity.clear()
    }

    private fun foregroundActivity(): Activity? =
        activity.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    private fun presentConsentIfPossible() {
        if (!consentFormPending || privacyFormShowing || presenting || disposed) return
        val host = foregroundActivity() ?: return
        consentFormPending = false
        invalidateLoads()
        privacyFormShowing = true
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(host) { error ->
            onMain {
                privacyFormShowing = false
                if (error != null) Log.w(TAG, "Consent form failed: ${error.errorCode}")
                updatePrivacyRequirement()
                if (!consent.canRequestAds()) invalidateLoads()
                initializeSdkIfAllowed()
                loadAd()
            }
        }
    }

    private fun updatePrivacyRequirement() {
        isPrivacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initializeSdkIfAllowed() {
        if (disposed || !consentRequested || !consent.canRequestAds()) return
        if (sdkReady) { loadAd(); return }
        if (sdkStarting) return
        sdkStarting = true
        Thread({
            try {
                MobileAds.initialize(appContext) {
                    main.post {
                        if (!disposed) {
                            sdkReady = true
                            sdkStarting = false
                            loadAd()
                        }
                    }
                }
            } catch (error: RuntimeException) {
                main.post {
                    sdkStarting = false
                    Log.w(TAG, "Ads initialization failed", error)
                }
            }
        }, "IlmeryaAdsInit").start()
    }

    override fun loadAd(): Unit = onMain {
        if (disposed || !sdkReady || !consent.canRequestAds() || privacyFormShowing ||
            loading || presenting || BuildConfig.ADMOB_INTERSTITIAL_ID.isBlank()) return@onMain
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt < MAX_AD_AGE_MS) return@onMain
        clearCachedAd()
        loading = true
        val requestToken = ++loadToken
        main.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
        InterstitialAd.load(appContext, BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) = onMain loadedCallback@{
                    if (disposed || requestToken != loadToken) return@loadedCallback
                    main.removeCallbacks(loadTimeout)
                    loading = false
                    if (!consent.canRequestAds() || privacyFormShowing) return@loadedCallback
                    ad = loaded
                    loadedAt = SystemClock.elapsedRealtime()
                    isAdReady = true
                    main.postDelayed(expireAd, MAX_AD_AGE_MS)
                    Log.d(TAG, "Game-over interstitial ready")
                }

                override fun onAdFailedToLoad(error: LoadAdError) = onMain failedCallback@{
                    if (requestToken != loadToken) return@failedCallback
                    main.removeCallbacks(loadTimeout)
                    loading = false
                    clearCachedAd()
                    Log.d(TAG, "Interstitial unavailable: ${error.code}")
                }
            })
    }

    override fun showInterstitial(onFinished: () -> Unit): Unit = onMain {
        val host = foregroundActivity()
        val ready = ad
        if (disposed || presenting || privacyFormShowing || !consent.canRequestAds() ||
            host == null || ready == null || SystemClock.elapsedRealtime() - loadedAt >= MAX_AD_AGE_MS) {
            if (ready != null && SystemClock.elapsedRealtime() - loadedAt >= MAX_AD_AGE_MS) clearCachedAd()
            loadAd()
            onFinished() // No late surprise ad if loading completes after game over.
            return@onMain
        }
        clearCachedAd()
        presenting = true
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                finishPresentation = null
                presenting = false
                onFinished()
                presentConsentIfPossible()
                loadAd()
            }
        }
        finishPresentation = finish
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = onMain(finish)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = onMain {
                Log.d(TAG, "Interstitial could not show: ${error.code}")
                finish()
            }
        }
        try { ready.show(host) } catch (error: RuntimeException) {
            Log.w(TAG, "Interstitial presentation failed", error)
            finish()
        }
    }

    override fun showPrivacyOptions(onFinished: () -> Unit): Unit = onMain {
        val host = foregroundActivity()
        if (disposed || host == null || presenting || privacyFormShowing || !isPrivacyOptionsRequired) {
            onFinished()
            return@onMain
        }
        invalidateLoads() // Do not reuse an ad requested under the previous privacy choices.
        privacyFormShowing = true
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                finishPrivacy = null
                privacyFormShowing = false
                updatePrivacyRequirement()
                initializeSdkIfAllowed()
                onFinished()
            }
        }
        finishPrivacy = finish
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error ->
            onMain {
                if (error != null) Log.w(TAG, "Privacy options failed: ${error.errorCode}")
                finish()
            }
        }
    }

    private fun clearCachedAd() {
        main.removeCallbacks(expireAd)
        ad = null
        isAdReady = false
        loadedAt = 0L
    }

    private fun invalidateLoads() {
        loadToken++
        loading = false
        main.removeCallbacks(loadTimeout)
        clearCachedAd()
    }

    fun dispose(): Unit = onMain {
        disposed = true
        activity.clear()
        invalidateLoads()
        finishPresentation?.invoke()
        finishPrivacy?.invoke()
        main.removeCallbacksAndMessages(null)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "IlmeryaAds"
        const val MAX_AD_AGE_MS = 60 * 60 * 1000L
        const val LOAD_TIMEOUT_MS = 30_000L
    }
}
