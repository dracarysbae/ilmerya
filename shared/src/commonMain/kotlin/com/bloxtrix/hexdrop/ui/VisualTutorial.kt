package com.bloxtrix.hexdrop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlinx.coroutines.delay
import kotlin.math.*

/** Uses the same opaque mineral renderer as the game, with an accessible text equivalent. */
@Composable fun VisualTutorial() {
    var page by remember { mutableStateOf(0) }
    var after by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val materials = LocalGameMaterials.current
    val measure = rememberTextMeasurer()
    LaunchedEffect(page, reduced) {
        after = false
        if (!reduced) while (true) { delay(1800); after = !after }
    }
    val motion by animateFloatAsState(if (after) 1f else 0f, tween(if (reduced) 0 else 650), label = "lesson")
    val titles = listOf(words("Yerleştir", "Place"), words("Birleştir", "Merge"), words("Zincir", "Cascade"), words("Devir", "Cycle"))
    // Each tab carries its own rule, so the whole guide fits one screen without scrolling.
    val caption = when (page) {
        0 -> words("Bir sütun seç; çerçeve taşın ineceği yuvayı gösterir. Yerleştir'e bas.",
            "Pick a column; the outline shows the socket your stone will land in. Press Place.")
        1 -> words("Aynı sayıdaki en az üç komşu taş birleşir ve iki kat değerli tek taşa dönüşür. Altı yönün hepsi sayılır.",
            "Three or more neighbouring stones of equal value fuse into one stone worth twice as much. All six directions count.")
        2 -> words("Taşlar boşluklara düşer, yeni birleşmeler zincir kurar. Her dalga daha çok puan ve 1 enerji verir.",
            "Stones fall into the gaps and new matches cascade. Every wave scores more and gives 1 energy.")
        else -> words("3 enerji: seçili sütunun en alt taşı en üste çıkar, diğerleri bir yuva iner; sıradaki taş değişmez. En fazla 6 enerji. Tahta dolsa bile Devir varsa oyun sürer.",
            "3 energy: the selected column's bottom stone moves to the top and the rest move down; your next stone stays. Up to 6 energy. A full board plays on while a Cycle exists.")
    }
    Column(Modifier.fillMaxWidth().neuSurface(18.dp, recessed = true, color = C.Board).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            titles.forEachIndexed { i, title ->
                TextButton(onClick = { page = i }, modifier=Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                    Text(title, color = if (page == i) C.Accent else C.TextDim, fontSize = 11.sp)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = caption }) {
            val r = min(size.width / 7.5f, size.height / 5.5f)
            val cx = size.width / 2; val cy = size.height / 2
            fun stone(x: Float, y: Float, v: Int) { drawHexCell(x, y, r, v, measure, materials = materials) }
            when (page) {
                0 -> {
                    for (i in 0..2) drawSocket(cx, cy + (i - 1) * r * 1.75f, r, materials = materials)
                    stone(cx, cy + r * 1.75f, 4)
                    stone(cx, cy - r * 1.75f * (1 - motion), 2)
                }
                1 -> {
                    val centers = listOf(Offset(cx-r*1.5f,cy-r*.87f),Offset(cx,cy),Offset(cx+r*1.5f,cy-r*.87f))
                    if (motion < .98f) centers.forEach { p -> stone(p.x+(cx-p.x)*motion,p.y+(cy-p.y)*motion,2) }
                    else stone(cx,cy,4)
                }
                2 -> {
                    if (motion < .7f) {
                        stone(cx-r*1.5f,cy+r*.87f,4); stone(cx+r*1.5f,cy+r*.87f,4)
                        stone(cx,cy-r*1.7f*(1-motion/.7f),4)
                    } else stone(cx,cy+r*.87f,8)
                }
                else -> {
                    val top = cy-r*1.75f
                    for(i in 0..2) drawSocket(cx,top+i*r*1.75f,r,materials=materials)
                    stone(cx,top+r*1.75f*motion,2)
                    stone(cx,cy+r*1.75f*motion,4)
                    stone(cx+sin(motion*PI).toFloat()*r*1.6f,cy+r*1.75f-motion*r*3.5f,8)
                }
            }
            drawLine(C.Accent.copy(alpha=.45f),Offset(size.width*.15f,size.height-3.dp.toPx()),Offset(size.width*.85f,size.height-3.dp.toPx()),2.dp.toPx(),StrokeCap.Round)
        }
        Text(caption,color=C.TextPrimary,fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.heightIn(min=54.dp))
        if (reduced) TextButton(onClick={after=!after}) { Text(words("Önce / sonra", "Before / after")) }
    }
}
