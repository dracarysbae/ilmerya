package com.bloxtrix.hexdrop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.audio.*
import com.bloxtrix.hexdrop.ads.LocalAdsManager
import com.bloxtrix.hexdrop.haptic.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import com.bloxtrix.hexdrop.viewmodel.HexDropViewModel

@Composable
fun GameScreen(vm: HexDropViewModel, preferences: Preferences, onSettings:()->Unit, onBackToMenu: () -> Unit) {
    val reducedMotion = preferences.reducedMotion
    val s by vm.state.collectAsState()
    val ranked by vm.ranked.collectAsState()
    val submission by vm.submission.collectAsState()
    val ads = LocalAdsManager.current
    val adPending by vm.gameOverAds.pending.collectAsState()
    val adCompletedRun by vm.gameOverAds.completedRun.collectAsState()
    LaunchedEffect(s.runId) { ads.loadAd() }
    var animating by remember { mutableStateOf(false) }
    // The game-end ad waits until the last cascade has visibly finished.
    LaunchedEffect(s.runId, s.phase, s.resolving, animating) {
        if (!animating) vm.gameOverAds.showOnce(s, ads)
    }
    val materials = LocalGameMaterials.current
    val sound = LocalSoundEngine.current
    val music = LocalMusicEngine.current
    val haptic = LocalHapticEngine.current
    var confirmRestart by remember { mutableStateOf(false) }
    // A turn animates and sounds once; returning from the menu shows the result silently.
    val fresh = remember(s.runId, s.turnKey) { s.turnKey > 0 && vm.claimTurnSound(s.runId, s.turnKey) }
    val boardEvent: (BoardEvent) -> Unit = { event ->
        when (event) {
            BoardEvent.Landed -> { sound.playLand(false); haptic.impact(HapticStyle.LIGHT) }
            is BoardEvent.Wave -> {
                val top = event.wave.groups.maxOf { it.value }
                sound.playMerge((top.countTrailingZeroBits() - 1).coerceAtLeast(0))
                music.triggerMergeAccent()
                if (event.index >= 1) { sound.playChainLink(event.index + 1); music.triggerChainAccent(event.index + 1) }
                haptic.impact(if (event.index >= 1) HapticStyle.HEAVY else HapticStyle.MEDIUM)
            }
        }
    }
    // A record is a finished run that set the best score; the best is updated as the run is played.
    val newRecord = s.score > 0 && s.score > vm.runStartBest
    LaunchedEffect(s.phase, animating) {
        if (s.phase == GamePhase.GameOver) { music.stop(); if (!animating && vm.claimGameOverSound(s.runId)) { if (newRecord) sound.playNewHighScore() else sound.playGameOver() } }
        else if (s.phase == GamePhase.Playing) music.start() else music.stop()
    }
    val filled = s.grid.sumOf { row -> row.count { it != null } }
    LaunchedEffect(filled > 27, preferences.dangerAlerts) {
        music.setIntensity(filled.toFloat() / (ROWS * COLS))
        if (filled > 27 && preferences.dangerAlerts && s.phase == GamePhase.Playing) sound.playWarning()
    }
    val preview = remember(s.grid, s.currentCol, s.current) {
        dropPiece(s.grid, s.currentCol, s.current)?.let { processBoard(it.grid) }
    }
    val playable = s.phase == GamePhase.Playing && !s.resolving && !animating
    // Back pauses a live board; after the result it returns to the menu. Open dialogs handle back themselves.
    com.bloxtrix.hexdrop.platform.PlatformBackHandler(enabled = s.phase != GamePhase.Paused) {
        if (s.phase == GamePhase.Playing) vm.pause() else onBackToMenu()
    }
    val modeLabel = if (ranked) words("HAFTALIK LİG", "WEEKLY LEAGUE") else words(
        if (s.mode == GameMode.Calm) "DİNGİN · AŞAMA ${s.incomingStage}" else "AKIŞ · SEVİYE ${s.level}",
        if (s.mode == GameMode.Calm) "CALM · STAGE ${s.incomingStage}" else "FLOW · LEVEL ${s.level}")
    Atmosphere(reducedMotion) {
      BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize()) {
        // Short phones (under ~720 dp tall) use one header row and tighter spacing so the board stays large.
        val compact = maxHeight < 720.dp
        Column(Modifier.fillMaxSize().widthIn(max = 540.dp).align(Alignment.Center)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            if (compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        SmallLabel(modeLabel)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${s.score}", color = C.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(words("EN İYİ ", "BEST ") + "${s.highScore}", color = C.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    Action(words("Duraklat", "Pause"), { vm.pause() })
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Wordmark(); SmallLabel(modeLabel) }
                    Action(words("Duraklat", "Pause"), { vm.pause() })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${s.score}", color = C.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        SmallLabel(words("PUAN", "SCORE"))
                    }
                    Column(horizontalAlignment = Alignment.End) { SmallLabel(words("EN İYİ", "BEST")); Text("${s.highScore}", color = C.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.fillMaxWidth().neuSurface(18.dp, recessed = true, color = C.Board, depth = 4.dp)
                .padding(horizontal = 14.dp, vertical = if (compact) 3.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crystal(s.current, Modifier.size(if (compact) 40.dp else 48.dp))
                    Column(Modifier.padding(start = 8.dp)) { SmallLabel(words("ŞİMDİ", "NOW")); Text("${s.current}", color = C.TextPrimary, fontWeight = FontWeight.Bold) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    SmallLabel(words("SIRADAKİLER", "UP NEXT"))
                    Row { s.nextQueue.forEach { Crystal(it, Modifier.size(if (compact) 28.dp else 32.dp)) } }
                }
            }
            Box(Modifier.fillMaxWidth().height(if (compact) 18.dp else 25.dp), contentAlignment = Alignment.Center) {
                val status = when {
                    s.phase == GamePhase.GameOver -> words("Oyun bitti", "Game over")
                    s.grid.isGameOver() && !canCycle(s) -> words("Tahta dolu. Devir için çevrilebilen başka bir sütun seç.", "Board full. Select another column that can Cycle.")
                    s.grid.isGameOver() -> words("Tahta dolu. Devir ile yer aç; Devir hakkın bitince oyun biter.", "Board full. Cycle to make room; the game ends when no Cycle is left.")
                    s.lastChains > 1 -> words("${s.lastChains} DALGALI ZİNCİR  +${s.lastGain}", "${s.lastChains}-WAVE CASCADE  +${s.lastGain}")
                    s.lastChains == 1 -> words("BİRLEŞME  +${s.lastGain}  ·  +1 ENERJİ", "FUSION  +${s.lastGain}  ·  +1 ENERGY")
                    else -> words("Aynı sayıdaki 3 komşu taşı buluştur.", "Connect 3 neighbouring stones of equal value.")
                }
                Text(status, color = if (s.lastChains > 0) C.Accent else C.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(minOf(maxWidth, maxHeight * .64f + 12.dp)).fillMaxHeight().carvedTray(materials?.walnut)) {
                    HexBoard(s, reducedMotion, fresh, { if (playable) { vm.selectColumn(it); sound.playMove() } },
                        boardEvent, { animating = it },
                        Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(COLS) { col ->
                    val label = words("Sütun ${col + 1}", "Column ${col + 1}")
                    Box(Modifier.weight(1f).heightIn(min = 48.dp)
                        .neuSurface(14.dp, recessed = col == s.currentCol, color = if (col == s.currentCol) C.Board else C.Surface, depth = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .semantics { contentDescription = label; selected = col == s.currentCol }
                        .clickable(enabled = playable, role = Role.Button) { vm.selectColumn(col); sound.playMove() }, contentAlignment = Alignment.Center) {
                        Text("${col + 1}", color = if (s.grid.isColumnFull(col)) C.Danger else if (col == s.currentCol) C.Accent else C.TextDim, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (s.mode == GameMode.Flow) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(C.Border, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(s.autoDropProgress.coerceIn(0f, 1f)).fillMaxHeight()
                        .background(if (s.autoDropProgress > .8f) C.Danger else C.Accent, RoundedCornerShape(4.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SmallLabel(words("ENERJİ", "ENERGY"))
                    repeat(6) { i -> Box(Modifier.size(9.dp, 6.dp).background(if (i < s.energy) C.Accent else C.Border, RoundedCornerShape(2.dp))) }
                    Text("${s.energy}/6", color = C.TextDim, fontSize = 11.sp)
                }
                Text(when { preview == null -> words("Sütun dolu", "Column full"); preferences.preview && preview.chains > 0 -> words("Önizleme: +${preview.score}", "Preview: +${preview.score}"); else -> words("Sütun ${s.currentCol + 1}", "Column ${s.currentCol + 1}") }, color = C.TextDim, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action(words("↻  Devir · 3", "↻  Cycle · 3"), { vm.cycle() }, Modifier.weight(1f), enabled = playable && canCycle(s),
                    highlight = playable && canCycle(s) && s.grid.isGameOver())
                Action(words("↓  Yerleştir", "↓  Place"), { vm.drop() }, Modifier.weight(1.25f), primary = true, enabled = playable && preview != null)
            }
            if (!compact) Text(words("Yeni torbalar: 2–${s.incomingMaxValue} · Sıradaki üç taşı planla.", "New bags: 2–${s.incomingMaxValue} · Plan for the next three stones."), color = C.TextDim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
      }
    }
    if (s.phase != GamePhase.Playing && !(s.phase == GamePhase.GameOver &&
            (s.resolving || animating || adPending || adCompletedRun != s.runId))) {
        val over = s.phase == GamePhase.GameOver
        IlmeryaDialog(if (over) words("Tahta doldu", "The board is full") else words("Bir nefes arası", "Take a breath"),
            onDismiss = { if (!over) vm.togglePause() else onBackToMenu() },
            corner = if (!over) ({ SettingsChip(words("Ayarlar", "Settings"), onSettings) }) else null) {
            if (over && newRecord) Text(words("Yeni rekor!", "New personal best!"), color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(words("${s.score} puan · ${s.dropCount} taş\nEn uzun zincir: ${s.bestChain} dalga", "${s.score} points · ${s.dropCount} stones\nBest cascade: ${s.bestChain} waves"), color = C.TextDim, lineHeight = 24.sp)
            if (!over) Action(words("Devam et", "Resume"), { vm.togglePause() }, Modifier.fillMaxWidth(), primary = true)
            if (over && ranked) Text(words(if(submission=="sending") "Lig sonucu gönderiliyor…" else if(submission=="saved") "Lig sonucu kaydedildi." else if(submission=="rejected") "Sunucu bu sonucu kabul etmedi; hafta veya oyun bileti kapanmış olabilir." else "Sonuç cihazda bekliyor; lig ekranından yeniden dene.",
                if(submission=="sending") "Submitting ranked result…" else if(submission=="saved") "Ranked result saved." else if(submission=="rejected") "The server rejected this result; the week or run ticket may have expired." else "Result is pending on this device; retry from the league screen."),color=C.Accent)
            Action(if(ranked) words("Serbest oyuna başla", "Start free play") else words("Yeniden başla", "Play again"), { if (over) vm.restart() else confirmRestart = true }, Modifier.fillMaxWidth(), primary = over)
            Action(words("Ana menü", "Main menu"), onBackToMenu, Modifier.fillMaxWidth())
        }
    }
    if (confirmRestart) IlmeryaDialog(words("Bu oyunu yeniden başlat?", "Restart this run?"), { confirmRestart = false }) {
        Text(words("Mevcut tahta sıfırlanır. En iyi puanın korunur.", "Your board will reset. Your best score is saved."), color = C.TextDim)
        DialogButtons(words("Vazgeç", "Cancel") to { confirmRestart = false }, words("Yeniden başlat", "Restart") to { confirmRestart = false; vm.restart() })
    }
}

/** Compact settings entry for a dialog's top-right corner: a drawn gear and a small label, 48 dp tall. */
@Composable
private fun SettingsChip(label: String, onClick: () -> Unit) {
    val sound = LocalSoundEngine.current
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = label) { sound.playButtonPress(); onClick() }
        .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(22.dp)) {
            val c = center; val r = size.minDimension / 2
            repeat(8) { i ->
                val a = i * kotlin.math.PI.toFloat() / 4
                drawLine(C.TextDim, c + androidx.compose.ui.geometry.Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * r * .55f,
                    c + androidx.compose.ui.geometry.Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * r * .98f, r * .28f,
                    androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(C.TextDim, r * .62f, c)
            drawCircle(C.Surface, r * .26f, c)
        }
        Text(label, color = C.TextDim, fontSize = 10.sp)
    }
}
