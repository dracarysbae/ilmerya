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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VisualTutorial()
        Rule("01", words("Yerini seç", "Choose a column"), words("Bir sütuna dokun. Renkli çerçeve, taşın nereye yerleşeceğini gösterir. Yerleştir düğmesine bas.", "Tap a column. The coloured outline shows where your stone will land. Press Place."))
        Rule("02", words("Üç taşı buluştur", "Connect three"), words("Aynı sayıdaki en az üç komşu taş birleşir ve iki kat değerli tek bir taşa dönüşür. Altı yöndeki komşular sayılır.", "At least three neighbouring stones of equal value fuse into one stone worth twice as much. All six directions count."))
        Rule("03", words("Zinciri kur", "Build a cascade"), words("Boşalan yerlere taşlar düşer. Yeni birleşmeler zincir oluşturur. Her dalga daha çok puan ve bir enerji kazandırır.", "Stones fall into the gaps. New matches create a cascade. Each wave earns more points and one energy."))
        Rule("04", words("Devir ile yön değiştir", "Turn the tide with Cycle"), words("3 enerji harca: seçili sütunun en alttaki taşı en üste çıkar, diğerleri birer yuva iner. Sıradaki taşın değişmez. Yeni komşuluklar, yeni zincirler!", "Spend 3 energy to lift the bottom stone of the selected column to the top; the others move down one socket. Your next stone stays the same. New neighbours, new cascades!"))
        Rule("05", words("İlerisini planla", "Plan ahead"), words("Başlangıçta 2, 4, 8 ve 16 gelir. 18 yerleştirmeden sonra açılan yeni torbalara 32, 54 yerleştirmeden sonra 64 katılır. Görünen sıradaki taşlar değişmez; küçük taşlar her zaman gelmeye devam eder.", "Start with 2, 4, 8 and 16. New bags add 32 after 18 placements and 64 after 54. Your preview stays unchanged, and small stones keep arriving."))
        Text(words("En fazla 6 enerji biriktirebilirsin. Tahta dolsa bile kullanılabilir bir Devir hamlen varsa oyun devam eder.", "You can store up to 6 energy. Even on a full board, the game goes on while you have a Cycle you can use."), color = C.Accent, fontSize = 13.sp)
    }
}
@Composable private fun Rule(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, color = C.Accent, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = C.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = C.TextDim, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun InfoDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = dismiss, containerColor = C.Surface,
        title = { Text(title, color = C.TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { TextButton(onClick = dismiss) { Text(words("Tamam", "Done"), color = C.Accent) } })
}

@Composable
fun Settings(preferences: Preferences, onChange: (Preferences) -> Unit, dismiss: () -> Unit, appVersion: String = "") {
    val ads = LocalAdsManager.current
    val sound = com.bloxtrix.hexdrop.audio.LocalSoundEngine.current
    InfoDialog(words("Ayarlar", "Settings"), dismiss) {
        SmallLabel(words("SES VE MÜZİK", "SOUND & MUSIC"))
        Setting(words("Taş ve arayüz sesleri", "Stone & interface sounds"), preferences.sound) { onChange(preferences.copy(sound = it)) }
        Text(words("Efekt düzeyi", "Effects volume") + " · ${(preferences.soundVolume * 100).toInt()}%", color = C.TextDim)
        val effectsLabel=words("Efekt düzeyi", "Effects volume")
        val musicLabel=words("Müzik düzeyi", "Music volume")
        Slider(preferences.soundVolume, { onChange(preferences.copy(soundVolume = it)) }, modifier=Modifier.semantics {contentDescription=effectsLabel}, enabled = preferences.sound,
            onValueChangeFinished = { sound.playMerge(2) })
        Setting(words("Oyun müziği", "Game music"), preferences.music) { onChange(preferences.copy(music = it)) }
        Text(words("Müzik düzeyi", "Music volume") + " · ${(preferences.musicVolume * 100).toInt()}%", color = C.TextDim)
        Slider(preferences.musicVolume, { onChange(preferences.copy(musicVolume = it)) }, modifier=Modifier.semantics {contentDescription=musicLabel}, enabled = preferences.music)
        SmallLabel(words("OYUN VE ERİŞİLEBİLİRLİK", "PLAY & ACCESSIBILITY"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words("Dil", "Language"), color = C.TextPrimary, modifier = Modifier.weight(1f))
            // Language names are written in their own language so either choice is recognisable.
            FilterChip(preferences.turkish, { onChange(preferences.copy(turkish = true)) }, label = { Text("Türkçe") },
                modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(!preferences.turkish, { onChange(preferences.copy(turkish = false)) }, label = { Text("English") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        Setting(words("Titreşim", "Haptics"), preferences.haptics) { onChange(preferences.copy(haptics = it)) }
        Setting(words("Hareketi azalt", "Reduce motion"), preferences.reducedMotion) { onChange(preferences.copy(reducedMotion = it)) }
        Setting(words("Birleşme puanı önizlemesi", "Merge score preview"), preferences.preview) { onChange(preferences.copy(preview = it)) }
        Setting(words("Tahta doluluk uyarısı", "Full-board warning"), preferences.dangerAlerts) { onChange(preferences.copy(dangerAlerts = it)) }
        Text(words("Müzik bu oyun için özgün olarak bestelendi. Hareketi azalt seçeneği oyun ve rehber animasyonlarını da durdurur.", "The music was composed for this game. Reduce motion also stops game and tutorial animations."), color = C.TextDim, fontSize = 12.sp)
        if (ads.isPrivacyOptionsRequired) {
            Action(words("Reklam gizliliği tercihleri", "Ad privacy choices"), { ads.showPrivacyOptions {} }, Modifier.fillMaxWidth())
        }
        if (appVersion.isNotEmpty()) Text(words("Sürüm", "Version") + " $appVersion · OzGAMES", color = C.TextDim, fontSize = 11.sp)
    }
}
@Composable private fun Setting(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = C.TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked, change, modifier=Modifier.semantics {contentDescription=label}, colors = SwitchDefaults.colors(checkedThumbColor = C.Background, checkedTrackColor = C.Accent))
    }
}
