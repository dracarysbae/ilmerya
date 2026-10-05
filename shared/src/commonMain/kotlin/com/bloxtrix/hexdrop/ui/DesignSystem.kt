package com.bloxtrix.hexdrop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import com.bloxtrix.hexdrop.ads.LocalAdsManager
import kotlin.math.*

val LocalTurkish = staticCompositionLocalOf { true }
val LocalReducedMotion = staticCompositionLocalOf { false }
@Composable fun words(tr: String, en: String) = if (LocalTurkish.current) tr else en

data class Preferences(val turkish: Boolean = true, val sound: Boolean = true, val haptics: Boolean = true,
    val reducedMotion: Boolean = false, val music: Boolean = true, val soundVolume: Float = .7f,
    val musicVolume: Float = .45f, val preview: Boolean = true, val dangerAlerts: Boolean = true,
    val tutorialSeen: Boolean = false)

@Composable
fun Atmosphere(reducedMotion: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(C.SurfaceHigh, C.Background, Color(0xFF343B40)), Offset.Zero, Offset(size.width, size.height)))
            drawCircle(Brush.radialGradient(listOf(C.Highlight.copy(alpha = .055f), Color.Transparent), Offset.Zero, size.width * 1.3f), size.width * 1.3f, Offset.Zero)
            repeat(1500) { i ->
                val x = ((i * 73 + 19) % 997) / 997f * size.width
                val y = ((i * 137 + 31) % 991) / 991f * size.height
                drawCircle(if (i % 3 == 0) Color.Black.copy(alpha = .055f) else Color.White.copy(alpha = .025f), .22.dp.toPx(), Offset(x, y))
            }
        }
        content()
    }
}

@Composable
fun Wordmark(large: Boolean = false) {
    Text(words("İLMERYA", "ILMERYA"), style = TextStyle(
        color = C.TextPrimary,
        fontWeight = FontWeight.Bold, fontSize = if (large) 46.sp else 23.sp,
        letterSpacing = if (large) 5.sp else 3.sp))
}

@Composable
/** [highlight] draws a slow accent pulse around the button, used when it is the only way forward. */
fun Action(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    highlight: Boolean = false) {
    val sound = com.bloxtrix.hexdrop.audio.LocalSoundEngine.current
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (reducedMotion) 0 else 100), label = "ceramicPress")
    val pulse = if (highlight && !reducedMotion) rememberInfiniteTransition(label = "actionPulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value
        else if (highlight) 1f else 0f
    Box(modifier.heightIn(min = 52.dp)
        .drawBehind {
            if (highlight) drawRoundRect(C.Accent.copy(alpha = .35f + .45f * pulse), cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke((1.5f + 2f * pulse).dp.toPx()))
        }
        .graphicsLayer { if (!reducedMotion) { translationY = press * 2.dp.toPx(); scaleX = 1f - press * .012f; scaleY = scaleX } }
        .neuSurface(18.dp, recessed = pressed || !enabled,
            color = if (primary && enabled) C.Accent else C.Surface, depth = if (enabled) 4.dp else 2.dp)
        .clip(shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = { sound.playButtonPress(); onClick() })
        .padding(horizontal = 14.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (!enabled) C.TextDim.copy(alpha = .62f) else if (primary) C.OnAccent else C.TextPrimary,
            fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = C.TextDim, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Rules() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VisualTutorial()
        Text(words("Yeni torbalar: başta 2–16 · 18 yerleştirmeden sonra 32 · 54'ten sonra 64. Küçük taşlar hep gelir.",
            "New bags: 2–16 at first · 32 after 18 placements · 64 after 54. Small stones always keep coming."),
            color = C.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/**
 * Every popup uses this container: it fits a small phone without scrolling, sits 16 dp from the
 * screen edges, and closes when the player taps outside it (unless [dismissOnOutside] is false).
 */
@Composable
fun IlmeryaDialog(title: String, onDismiss: () -> Unit, corner: (@Composable () -> Unit)? = null,
    dismissOnOutside: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnOutside)) {
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 440.dp).fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)).background(C.Surface).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                corner?.invoke()
            }
            content()
        }
    }
}

/** Right-aligned text action row used at the bottom of popups. */
@Composable
fun DialogButtons(vararg buttons: Pair<String, () -> Unit>, footer: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(footer, color = C.TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
        buttons.forEachIndexed { i, (label, action) ->
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label, color = if (i == buttons.lastIndex) C.Accent else C.TextDim, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InfoDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    IlmeryaDialog(title, dismiss) {
        content()
        DialogButtons(words("Tamam", "Done") to dismiss)
    }
}

@Composable
fun Settings(preferences: Preferences, onChange: (Preferences) -> Unit, dismiss: () -> Unit, appVersion: String = "") {
    val ads = LocalAdsManager.current
    val sound = com.bloxtrix.hexdrop.audio.LocalSoundEngine.current
    IlmeryaDialog(words("Ayarlar", "Settings"), dismiss) {
        SmallLabel(words("SES VE MÜZİK", "SOUND & MUSIC"))
        AudioRow(words("Efektler", "Effects"), preferences.sound, preferences.soundVolume,
            { onChange(preferences.copy(sound = it)) }, { onChange(preferences.copy(soundVolume = it)) }, { sound.playMerge(2) })
        AudioRow(words("Müzik", "Music"), preferences.music, preferences.musicVolume,
            { onChange(preferences.copy(music = it)) }, { onChange(preferences.copy(musicVolume = it)) }, {})
        SmallLabel(words("OYUN", "PLAY"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words("Dil", "Language"), color = C.TextPrimary, modifier = Modifier.weight(1f))
            // Language names are written in their own language so either choice is recognisable.
            FilterChip(preferences.turkish, { onChange(preferences.copy(turkish = true)) }, label = { Text("Türkçe") },
                modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(!preferences.turkish, { onChange(preferences.copy(turkish = false)) }, label = { Text("English") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleTile(words("Titreşim", "Haptics"), preferences.haptics, Modifier.weight(1f)) { onChange(preferences.copy(haptics = it)) }
            ToggleTile(words("Hareketi azalt", "Reduce motion"), preferences.reducedMotion, Modifier.weight(1f)) { onChange(preferences.copy(reducedMotion = it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleTile(words("Puan önizlemesi", "Score preview"), preferences.preview, Modifier.weight(1f)) { onChange(preferences.copy(preview = it)) }
            ToggleTile(words("Doluluk uyarısı", "Full-board alert"), preferences.dangerAlerts, Modifier.weight(1f)) { onChange(preferences.copy(dangerAlerts = it)) }
        }
        if (ads.isPrivacyOptionsRequired) {
            Action(words("Reklam gizliliği tercihleri", "Ad privacy choices"), { ads.showPrivacyOptions {} }, Modifier.fillMaxWidth())
        }
        DialogButtons(words("Tamam", "Done") to dismiss,
            footer = if (appVersion.isNotEmpty()) words("Sürüm", "Version") + " $appVersion · OzGAMES" else "OzGAMES")
    }
}

/** One line: name, volume slider and on/off switch. */
@Composable private fun AudioRow(label: String, on: Boolean, volume: Float, toggle: (Boolean) -> Unit,
    setVolume: (Float) -> Unit, release: () -> Unit) {
    val volumeLabel = "$label · ${(volume * 100).toInt()}%"
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = C.TextPrimary, modifier = Modifier.width(72.dp), maxLines = 1)
        Slider(volume, setVolume, Modifier.weight(1f).semantics { contentDescription = volumeLabel }, enabled = on,
            onValueChangeFinished = release,
            colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Border))
        Switch(on, toggle, modifier = Modifier.semantics { contentDescription = label },
            colors = SwitchDefaults.colors(checkedThumbColor = C.Background, checkedTrackColor = C.Accent))
    }
}

/** Compact two-per-row toggle: recessed with an accent lamp when on. */
@Composable private fun ToggleTile(label: String, on: Boolean, modifier: Modifier, change: (Boolean) -> Unit) {
    Row(modifier.heightIn(min = 52.dp).neuSurface(14.dp, recessed = on, color = if (on) C.Board else C.Surface, depth = 2.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(role = Role.Switch, onClickLabel = label) { change(!on) }
        .semantics { contentDescription = label; stateDescription = if (on) "on" else "off" }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (on) C.Accent else C.Border))
        Text(label, color = if (on) C.TextPrimary else C.TextDim, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
    }
}
