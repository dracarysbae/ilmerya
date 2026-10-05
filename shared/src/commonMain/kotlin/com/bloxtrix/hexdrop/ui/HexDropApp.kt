package com.bloxtrix.hexdrop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.bloxtrix.hexdrop.audio.*
import com.bloxtrix.hexdrop.haptic.LocalHapticEngine
import com.bloxtrix.hexdrop.model.GameMode
import com.bloxtrix.hexdrop.model.GamePhase
import com.bloxtrix.hexdrop.platform.PlatformBackHandler
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel

private enum class Screen { Menu, Game, Weekly }

/** A start request that would replace an unfinished run waits for confirmation. */
private sealed interface StartRequest {
    data class Free(val mode: GameMode) : StartRequest
    data object League : StartRequest
}

@Composable
fun HexDropApp(vm: HexDropViewModel, initialPreferences: Preferences = Preferences(), appVersion: String = "",
    demoScene: String? = null, savePreferences: (Preferences) -> Unit = {}) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Menu.name) }
    val screen = Screen.valueOf(screenName)
    fun go(next: Screen) { screenName = next.name }
    var settings by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(initialPreferences) }
    var pendingMode by remember { mutableStateOf<GameMode?>(null) }
    var replace by remember { mutableStateOf<StartRequest?>(null) }
    val state by vm.state.collectAsState()
    val ranked by vm.ranked.collectAsState()
    val starting by vm.leagueStarting.collectAsState()
    val restoreNotice by vm.restoreNotice.collectAsState()
    val sound = LocalSoundEngine.current
    val music = LocalMusicEngine.current
    val haptics = LocalHapticEngine.current
    LaunchedEffect(preferences) {
        sound.setEnabled(preferences.sound); music.setEnabled(preferences.music)
        sound.setVolume(preferences.soundVolume); music.setVolume(preferences.musicVolume)
        haptics.setEnabled(preferences.haptics)
        savePreferences(preferences)
    }
    LaunchedEffect(screen) { if (screen != Screen.Game) music.stop() }
    val canResume = state.dropCount > 0 && state.phase != GamePhase.GameOver || ranked && state.phase != GamePhase.GameOver
    fun startFree(mode: GameMode) {
        if (!preferences.tutorialSeen) pendingMode = mode else { vm.restart(mode); go(Screen.Game) }
    }
    fun startLeague() = vm.startLeague { go(Screen.Game) }
    PlatformBackHandler(enabled = screen == Screen.Weekly) { go(Screen.Menu) }
    // Store screenshots (debug/QA builds pass a scene); never reachable from the release UI.
    LaunchedEffect(demoScene) {
        when (demoScene) {
            "board" -> { vm.showDemo(DemoScenes.board()); go(Screen.Game) }
            "result" -> { val over = DemoScenes.result(); vm.showDemo(over, startBest = over.score / 2); go(Screen.Game) }
            "tutorial" -> pendingMode = GameMode.Calm
            "settings" -> settings = true
            else -> Unit
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = C.Accent, onPrimary = C.OnAccent,
        background = C.Background, onBackground = C.TextPrimary, surface = C.Surface,
        onSurface = C.TextPrimary, surfaceVariant = C.EmptyCell, onSurfaceVariant = C.TextDim,
        surfaceTint = C.Surface, outline = C.BorderBright)) {
        CompositionLocalProvider(
            LocalTurkish provides preferences.turkish,
            LocalReducedMotion provides preferences.reducedMotion,
            LocalGameMaterials provides loadGameMaterials(),
        ) {
            when (screen) {
                Screen.Weekly -> WeeklyScreen(starting, { if (canResume) replace = StartRequest.League else startLeague() }, { go(Screen.Menu) })
                Screen.Game -> GameScreen(vm, preferences, { settings = true }) { vm.pause(); go(Screen.Menu) }
                Screen.Menu -> MenuScreen(state.highScore, preferences.reducedMotion,
                    { mode -> if (canResume) replace = StartRequest.Free(mode) else startFree(mode) },
                    { settings = true }, { go(Screen.Weekly) },
                    canResume = canResume, resumeRanked = ranked,
                    onResume = { vm.resume(); go(Screen.Game) })
            }
            if (settings) Settings(preferences, { preferences = it }, { settings = false }, appVersion)
            replace?.let { request ->
                IlmeryaDialog(words("Yarım kalan oyun silinsin mi?", "Replace your unfinished run?"), { replace = null }) {
                    Text(if (ranked) words("Devam eden lig oyunun kapanır ve bu hafta için sayılmaz. Yeni oyun onun yerine başlar.",
                            "Your unfinished ranked run will close and will not count this week. A new game starts in its place.")
                        else words("Kaldığın tahta silinir ve yeni oyun başlar. En iyi puanın korunur.",
                            "Your current board will be cleared and a new game will start. Your best score is kept."), color = C.TextDim)
                    DialogButtons(words("Vazgeç", "Cancel") to { replace = null }, words("Yeni oyun başlat", "Start new game") to {
                        replace = null
                        when (request) { is StartRequest.Free -> startFree(request.mode); StartRequest.League -> startLeague() }
                    })
                }
            }
            if (restoreNotice == "expired" && screen == Screen.Menu) {
                IlmeryaDialog(words("Lig bileti kapandı", "League ticket closed"), vm::clearRestoreNotice) {
                    Text(words("Kaydedilen lig oyununun süresi doldu. Tahtan korunarak serbest oyun olarak devam edebilirsin; bu oyun sıralamaya girmez.",
                        "The saved ranked run expired. Your board is kept as a free game; it will not enter the standings."), color = C.TextDim)
                    DialogButtons(words("Tamam", "OK") to vm::clearRestoreNotice)
                }
            }
            pendingMode?.let { mode ->
                IlmeryaDialog(words("İlk taşından önce", "Before your first stone"), { pendingMode = null }) {
                    Rules()
                    DialogButtons(words("Geri", "Back") to { pendingMode = null }, words("Başla", "Let's play") to {
                        preferences = preferences.copy(tutorialSeen = true); pendingMode = null; vm.restart(mode); go(Screen.Game)
                    })
                }
            }
        }
    }
}
