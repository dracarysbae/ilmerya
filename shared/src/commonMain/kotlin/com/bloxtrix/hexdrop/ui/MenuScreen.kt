package com.bloxtrix.hexdrop.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.model.GameMode
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlin.math.*

@Composable
fun MenuScreen(highScore: Long, reducedMotion: Boolean, onPlay: (GameMode) -> Unit, onSettings: () -> Unit,
    onWeekly:()->Unit, canResume:Boolean=false, resumeRanked:Boolean=false, onResume:()->Unit={}) {
    var help by remember { mutableStateOf(false) }
    val materials = LocalGameMaterials.current
    Atmosphere(reducedMotion) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().widthIn(max = 520.dp).align(Alignment.Center)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { SmallLabel(words("EN İYİ", "PERSONAL BEST")); Text("$highScore", color = C.Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Action(words("Ayarlar", "Settings"), onSettings)
            }
            Spacer(Modifier.height(28.dp))
            SmallLabel(words("BİR TAŞ. BİNLERCE OLASILIK.", "ONE STONE. ENDLESS POSSIBILITIES."))
            Spacer(Modifier.height(12.dp))
            Wordmark(large = true)
            Text(words("Birleştir. Çevir. Akışı değiştir.", "Connect. Cycle. Change the flow."), color = C.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            val measure = rememberTextMeasurer()
            Canvas(Modifier.fillMaxWidth().height(215.dp)) {
                val r = min(size.width / 7.8f, size.height / 5.8f)
                val center = Offset(size.width / 2, size.height / 2)
                val tray = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(r * 2.7f, r * 2.7f), center + Offset(r * 2.7f, r * 2.7f))) }
                softShape(tray, C.Board, 7.dp.toPx(), recessed = true)
                if (materials != null) {
                    clipPath(tray) {
                        drawImage(materials.walnut,
                            dstOffset = IntOffset((center.x - r * 2.7f).roundToInt(), (center.y - r * 2.7f).roundToInt()),
                            dstSize = IntSize((r * 5.4f).roundToInt(), (r * 5.4f).roundToInt()), filterQuality = FilterQuality.Medium)
                        for (step in 9 downTo 1) {
                            translate(3.dp.toPx(), 4.dp.toPx()) {
                                drawPath(tray, Color(0xFF180C04).copy(alpha = .09f), style = Stroke(step * 3.dp.toPx()))
                            }
                        }
                    }
                    drawPath(tray, Brush.linearGradient(listOf(Color(0xFF251509), Color(0xFF6F4D31), Color(0xFFB68B55)),
                        center - Offset(r * 2.7f, r * 2.7f), center + Offset(r * 2.7f, r * 2.7f)), style = Stroke(1.3.dp.toPx()))
                }
                for (i in 0 until 6) {
                    val a = PI.toFloat() / 3 * i - PI.toFloat() / 6
                    val p = center + Offset(cos(a), sin(a)) * r * 1.78f
                    drawSocket(p.x, p.y, r * .87f, materials = materials,
                        woodBounds = androidx.compose.ui.geometry.Rect(center - Offset(r * 2.7f, r * 2.7f), center + Offset(r * 2.7f, r * 2.7f)))
                    drawHexCell(p.x, p.y, r * .83f, listOf(2, 4, 8, 16, 32, 64)[i], measure, materials = materials, textureVariant = i)
                }
                drawHexCell(center.x, center.y, r * 1.02f, 2, measure, materials = materials)
            }
            Row(Modifier.fillMaxWidth().neuSurface(20.dp, recessed = true, color = C.Board, depth = 2.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↻", color = C.Accent, fontSize = 36.sp, modifier = Modifier.padding(end = 16.dp))
                Column {
                    Text(words("Her düşüşün bir dönüşü var.", "Every fall has a turning point."), color = C.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(words("Enerji biriktir. Sütunu çevir. Zinciri sen kur.", "Store energy. Cycle a column. Build your cascade."), color = C.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            if(canResume) {
                Action(if(resumeRanked) words("Lig oyununa devam et", "Continue ranked run") else words("Kaldığın yerden devam et", "Continue your run"),onResume,Modifier.fillMaxWidth(),primary=true)
                Spacer(Modifier.height(16.dp))
            }
            Action(words("DİNGİN  ·  Kendi ritminde", "CALM  ·  At your own pace"), { onPlay(GameMode.Calm) }, Modifier.fillMaxWidth(), primary = !canResume)
            Spacer(Modifier.height(16.dp))
            Action(words("HAFTALIK LİG  ·  Sıralamanı gör", "WEEKLY LEAGUE  ·  View standings"),onWeekly,Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Action(words("AKIŞ  ·  Ritmi yakala", "FLOW  ·  Find your rhythm"), { onPlay(GameMode.Flow) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(words("Dingin: süre yok. Akış: taşlar zamanla düşer.", "Calm: no timer. Flow: stones fall on a beat."), color = C.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Action(words("Nasıl oynanır?", "How to play"), { help = true })
            Text("OzGAMES  ·  " + words("İLMERYA", "ILMERYA"), color = C.TextDim, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
        }
    }
    if (help) InfoDialog(words("Nasıl oynanır?", "How to play"), { help = false }) { Rules() }
}
