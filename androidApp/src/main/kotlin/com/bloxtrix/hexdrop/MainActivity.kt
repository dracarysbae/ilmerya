package com.bloxtrix.hexdrop

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bloxtrix.hexdrop.ads.AndroidAdsManager
import com.bloxtrix.hexdrop.ads.LocalAdsManager
import com.bloxtrix.hexdrop.audio.LocalMusicEngine
import com.bloxtrix.hexdrop.audio.LocalSoundEngine
import com.bloxtrix.hexdrop.audio.MusicEngine
import com.bloxtrix.hexdrop.audio.SoundEngine
import com.bloxtrix.hexdrop.competition.LocalLeague
import com.bloxtrix.hexdrop.competition.createAndroidLeague
import com.bloxtrix.hexdrop.haptic.HapticEngine
import com.bloxtrix.hexdrop.haptic.LocalHapticEngine
import com.bloxtrix.hexdrop.model.GamePhase
import com.bloxtrix.hexdrop.persistence.RunStore
import com.bloxtrix.hexdrop.ui.HexDropApp
import com.bloxtrix.hexdrop.ui.Preferences
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel
import com.google.android.gms.games.PlayGamesSdk
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val session by lazy { ViewModelProvider(this)[GameSession::class.java] }
    private val adsManager get() = session.ads
    private val viewModel get() = session.game
    private val soundEngine = SoundEngine()
    private val musicEngine = MusicEngine()
    private val hapticEngine = HapticEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.PGS_CONFIGURED) PlayGamesSdk.initialize(this)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        adsManager.initialize(this)
        hapticEngine.init(this)
        session.bindLeague(this)
        // A player who joined before is reconnected without UI; others are never signed in automatically.
        if (savedInstanceState == null) lifecycleScope.launch {
            if (session.competition.state.value.loginRequired) session.competition.signInSilently()
        }

        val prefs = getSharedPreferences("ilmerya", MODE_PRIVATE)
        val initial = Preferences(
            turkish = prefs.getBoolean("turkish", resources.configuration.locales[0].language == "tr"),
            sound = prefs.getBoolean("sound", true),
            music = prefs.getBoolean("music", prefs.getBoolean("sound", true)),
            soundVolume = prefs.getFloat("soundVolume", .7f).coerceIn(0f, 1f),
            musicVolume = prefs.getFloat("musicVolume", .45f).coerceIn(0f, 1f),
            preview = prefs.getBoolean("preview", true),
            dangerAlerts = prefs.getBoolean("dangerAlerts", true),
            tutorialSeen = prefs.getBoolean("tutorialSeen", false),
            haptics = prefs.getBoolean("haptics", true),
            reducedMotion = prefs.getBoolean("reducedMotion", false))
        setContent {
            CompositionLocalProvider(
                LocalAdsManager provides adsManager,
                LocalSoundEngine provides soundEngine,
                LocalMusicEngine provides musicEngine,
                LocalHapticEngine provides hapticEngine,
                LocalLeague provides session.competition,
            ) {
                // adb shell am start -n <package>/com.bloxtrix.hexdrop.MainActivity --es ilmerya.scene board (debug/QA only)
                val scene = if (BuildConfig.SCREENSHOT_SCENES) intent?.getStringExtra("ilmerya.scene") else null
                HexDropApp(vm = viewModel, initialPreferences = initial, appVersion = BuildConfig.VERSION_NAME, demoScene = scene,
                    savePreferences = { p -> prefs.edit().putBoolean("turkish", p.turkish)
                        .putBoolean("sound", p.sound).putBoolean("haptics", p.haptics)
                        .putBoolean("music", p.music).putFloat("soundVolume", p.soundVolume).putFloat("musicVolume", p.musicVolume)
                        .putBoolean("preview", p.preview).putBoolean("dangerAlerts", p.dangerAlerts).putBoolean("tutorialSeen", p.tutorialSeen)
                        .putBoolean("reducedMotion", p.reducedMotion).apply() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adsManager.bindActivity(this)
        session.bindLeague(this)
        if (viewModel.state.value.phase == GamePhase.Playing) musicEngine.start()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
        adsManager.unbindActivity(this)
        musicEngine.stop()
        soundEngine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        session.unbindLeague(this)
        soundEngine.release()
        musicEngine.release()
    }
}

class GameSession(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("ilmerya", Context.MODE_PRIVATE)
    private val runPrefs = application.getSharedPreferences("ilmerya_run", Context.MODE_PRIVATE)
    /** The unfinished board survives process death; apply() is flushed before a backgrounded process is killed. */
    private val runStore = object : RunStore {
        override fun load(): String? = runPrefs.getString("run", null)
        override fun save(encoded: String?) {
            if (encoded == null) runPrefs.edit().remove("run").apply() else runPrefs.edit().putString("run", encoded).apply()
        }
    }
    val game = HexDropViewModel(prefs.getLong("best", 0L), { best -> prefs.edit().putLong("best", best).apply() },
        runStore, System::currentTimeMillis)
    private val league = createAndroidLeague(application)
    val competition = league.first.also { game.league = it }
    val ads = AndroidAdsManager(application)
    fun bindLeague(host: android.app.Activity) = league.second.bind(host)
    fun unbindLeague(host: android.app.Activity) = league.second.unbind(host)
    override fun onCleared() { ads.dispose(); game.dispose() }
}
