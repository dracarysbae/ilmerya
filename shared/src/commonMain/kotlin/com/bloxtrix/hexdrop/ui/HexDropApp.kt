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
    savePreferences: (Preferences) -> Unit = {}) {
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
                AlertDialog(onDismissRequest = { replace = null }, containerColor = C.Surface,
                    title = { Text(words("Yarım kalan oyun silinsin mi?", "Replace your unfinished run?"), color = C.TextPrimary) },
                    text = { Text(if (ranked) words("Devam eden lig oyunun kapanır ve bu hafta için sayılmaz. Yeni oyun onun yerine başlar.",
                            "Your unfinished ranked run will close and will not count this week. A new game starts in its place.")
                        else words("Kaldığın tahta silinir ve yeni oyun başlar. En iyi puanın korunur.",
                            "Your current board will be cleared and a new game will start. Your best score is kept."), color = C.TextDim) },
                    confirmButton = { TextButton(onClick = {
                        replace = null
                        when (request) { is StartRequest.Free -> startFree(request.mode); StartRequest.League -> startLeague() }
                    }) { Text(words("Yeni oyun başlat", "Start new game"), color = C.Accent) } },
                    dismissButton = { TextButton(onClick = { replace = null }) { Text(words("Vazgeç", "Cancel")) } })
            }
            if (restoreNotice == "expired" && screen == Screen.Menu) {
                AlertDialog(onDismissRequest = vm::clearRestoreNotice, containerColor = C.Surface,
                    title = { Text(words("Lig bileti kapandı", "League ticket closed"), color = C.TextPrimary) },
                    text = { Text(words("Kaydedilen lig oyununun süresi doldu. Tahtan korunarak serbest oyun olarak devam edebilirsin; bu oyun sıralamaya girmez.",
                        "The saved ranked run expired. Your board is kept as a free game; it will not enter the standings."), color = C.TextDim) },
                    confirmButton = { TextButton(onClick = vm::clearRestoreNotice) { Text(words("Tamam", "OK"), color = C.Accent) } })
            }
            pendingMode?.let { mode ->
                AlertDialog(onDismissRequest = { pendingMode = null }, containerColor = C.Surface,
                    title = { Text(words("İlk taşından önce", "Before your first stone")) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { Rules() } },
                    confirmButton = { TextButton(onClick = {
                        preferences = preferences.copy(tutorialSeen = true); pendingMode = null; vm.restart(mode); go(Screen.Game)
                    }) { Text(words("Başla", "Let's play"), color = C.Accent) } },
                    dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(words("Geri", "Back")) } })
            }
        }
    }
}
