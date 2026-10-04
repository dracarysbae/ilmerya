package com.bloxtrix.hexdrop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import com.bloxtrix.hexdrop.ads.AdsManager
import com.bloxtrix.hexdrop.ads.LocalAdsManager
import com.bloxtrix.hexdrop.audio.LocalMusicEngine
import com.bloxtrix.hexdrop.audio.LocalSoundEngine
import com.bloxtrix.hexdrop.audio.MusicEngine
import com.bloxtrix.hexdrop.audio.SoundEngine
import com.bloxtrix.hexdrop.audio.configureGameAudioSession
import com.bloxtrix.hexdrop.competition.GameCenterBridge
import com.bloxtrix.hexdrop.competition.LeagueRepository
import com.bloxtrix.hexdrop.competition.LocalLeague
import com.bloxtrix.hexdrop.competition.createIosLeague
import com.bloxtrix.hexdrop.haptic.HapticEngine
import com.bloxtrix.hexdrop.haptic.LocalHapticEngine
import com.bloxtrix.hexdrop.persistence.RunStore
import com.bloxtrix.hexdrop.ui.HexDropApp
import com.bloxtrix.hexdrop.ui.Preferences
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

/**
 * Owned once by the SwiftUI app. The native host forwards scene lifecycle events.
 * [gameCenter] is the Swift GameKit bridge; [apiUrl] is the HTTPS league service (empty disables the league).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
class IosGameSession(private val adsManager: AdsManager, gameCenter: GameCenterBridge?, apiUrl: String) {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val scope = MainScope()
    private val runStore = object : RunStore {
        override fun load(): String? = defaults.stringForKey("ilmerya.run")
        override fun save(encoded: String?) {
            if (encoded == null) defaults.removeObjectForKey("ilmerya.run") else defaults.setObject(encoded, forKey = "ilmerya.run")
        }
    }
    private val game = HexDropViewModel(defaults.integerForKey("ilmerya.best"), {
        defaults.setInteger(it, forKey = "ilmerya.best")
    }, runStore, { (NSDate().timeIntervalSince1970 * 1000).toLong() })
    private val league = createIosLeague(apiUrl, gameCenter).also { game.league = it }
    private val sound: SoundEngine
    private val music: MusicEngine
    private val haptics = HapticEngine()
    private val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    init {
        // Ambient: respects the silent switch and mixes with the player's own audio.
        configureGameAudioSession()
        sound = SoundEngine()
        music = MusicEngine()
        // Only a player who joined earlier is reconnected silently.
        (league as? LeagueRepository)?.let { repository ->
            scope.launch { if (repository.state.value.loginRequired) repository.signInSilently() }
        }
    }

    private fun savedBoolean(key: String, fallback: Boolean): Boolean =
        if (defaults.objectForKey("ilmerya.$key") == null) fallback
        else defaults.boolForKey("ilmerya.$key")

    fun makeViewController(): UIViewController = ComposeUIViewController {
        CompositionLocalProvider(
            LocalAdsManager provides adsManager,
            LocalSoundEngine provides sound,
            LocalMusicEngine provides music,
            LocalHapticEngine provides haptics,
            LocalLeague provides league,
        ) {
            val systemTurkish = (NSLocale.preferredLanguages.firstOrNull() as? String)?.startsWith("tr") ?: true
            HexDropApp(game, initialPreferences = Preferences(
                turkish = savedBoolean("turkish", systemTurkish),
                sound = savedBoolean("sound", true),
                music = savedBoolean("music", savedBoolean("sound", true)),
                soundVolume = if (defaults.objectForKey("ilmerya.soundVolume") == null) .7f else defaults.floatForKey("ilmerya.soundVolume").coerceIn(0f, 1f),
                musicVolume = if (defaults.objectForKey("ilmerya.musicVolume") == null) .45f else defaults.floatForKey("ilmerya.musicVolume").coerceIn(0f, 1f),
                preview = savedBoolean("preview", true),
                dangerAlerts = savedBoolean("dangerAlerts", true),
                tutorialSeen = savedBoolean("tutorialSeen", false),
                haptics = savedBoolean("haptics", true),
                reducedMotion = savedBoolean("reducedMotion", false)),
                appVersion = version,
                savePreferences = { preferences ->
                    defaults.setBool(preferences.turkish, forKey = "ilmerya.turkish")
                    defaults.setBool(preferences.sound, forKey = "ilmerya.sound")
                    defaults.setBool(preferences.music, forKey = "ilmerya.music")
                    defaults.setFloat(preferences.soundVolume, forKey = "ilmerya.soundVolume")
                    defaults.setFloat(preferences.musicVolume, forKey = "ilmerya.musicVolume")
                    defaults.setBool(preferences.preview, forKey = "ilmerya.preview")
                    defaults.setBool(preferences.dangerAlerts, forKey = "ilmerya.dangerAlerts")
                    defaults.setBool(preferences.tutorialSeen, forKey = "ilmerya.tutorialSeen")
                    defaults.setBool(preferences.haptics, forKey = "ilmerya.haptics")
                    defaults.setBool(preferences.reducedMotion, forKey = "ilmerya.reducedMotion")
                })
        }
    }

    /** Scene became inactive or went to the background: pause play and silence audio. */
    fun pause() { game.pause(); music.stop(); sound.stop() }
    fun dispose() { scope.cancel(); game.dispose(); sound.release(); music.release() }
}
