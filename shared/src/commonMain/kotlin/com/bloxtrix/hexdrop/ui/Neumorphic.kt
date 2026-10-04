package com.bloxtrix.hexdrop.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlin.math.*

/** Directional light and contact shadows, with no bright full-perimeter halo. */
fun DrawScope.softShape(path: Path, base: Color, depth: Float, recessed: Boolean = false) {
    val bounds = path.getBounds()
    if (!recessed) {
        for (step in 6 downTo 1) {
            translate(depth * .45f, depth * .8f) {
                drawPath(path, C.Shadow.copy(alpha = .065f), style = Stroke(depth * step * .35f))
            }
        }
        drawPath(path, Brush.linearGradient(listOf(lerp(base, Color.White, .035f), base, lerp(base, Color.Black, .08f)), bounds.topLeft, bounds.bottomRight))
        drawPath(path, Brush.linearGradient(listOf(C.Highlight.copy(alpha = .26f), Color.Transparent, C.Shadow.copy(alpha = .3f)), bounds.topLeft, bounds.bottomRight), style = Stroke(.7.dp.toPx()))
    } else {
        drawPath(path, Brush.linearGradient(listOf(lerp(base, Color.Black, .10f), lerp(base, Color.White, .035f)), bounds.topLeft, bounds.bottomRight))
        clipPath(path) {
            for (step in 8 downTo 1) {
                translate(depth * .7f, depth * .85f) {
                    drawPath(path, C.Shadow.copy(alpha = .09f), style = Stroke(depth * step * .55f))
                }
            }
        }
        drawPath(path, Brush.linearGradient(listOf(C.Shadow.copy(alpha = .75f), Color.Transparent, C.Highlight.copy(alpha = .48f)), bounds.topLeft, bounds.bottomRight), style = Stroke(1.dp.toPx()))
    }
}

/** Long, slightly wandering fibres bent around a small knot in the timber. */
private data class WoodFibre(val path: Path, val width: Float, val color: Color)

private fun woodFibres(bounds: Rect, unit: Float): List<WoodFibre> = List(190) { index ->
    val t = index / 189f
    val baseX = bounds.left + bounds.width * t
    val knotX = bounds.left + bounds.width * .79f
    val knotY = bounds.top + bounds.height * .22f
    val path = Path()
    for (step in 0..76) {
        val y = bounds.top + bounds.height * step / 76f
        val dx = (baseX - knotX) / (bounds.width * .17f)
        val dy = (y - knotY) / (bounds.height * .13f)
        val knotBend = dx * exp(-dx * dx * .7f - dy * dy) * unit * 16f
        val drift = sin(step * .083f + t * 5f) * unit * 3.5f + sin(step * .23f + index * .33f) * unit * .55f
        val x = baseX + drift + knotBend
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    WoodFibre(path, unit * if (index % 9 == 0) .65f else .18f + (index % 4) * .09f,
        if (index % 5 == 0) Color(0xFFDDB17A).copy(alpha = .15f) else Color(0xFF241408).copy(alpha = if (index % 9 == 0) .24f else .14f))
}

/** A pocket carved into solid walnut, with a thick lip, inner wall and timber floor. */
fun Modifier.carvedTray(texture: ImageBitmap? = null, radius: Dp = 28.dp): Modifier = drawWithCache {
    val bounds = Rect(Offset.Zero, size)
    val opening = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius.toPx()))) }
    val floorBounds = Rect(10.dp.toPx(), 15.dp.toPx(), size.width - 9.dp.toPx(), size.height - 10.dp.toPx())
    val floor = Path().apply { addRoundRect(RoundRect(floorBounds, CornerRadius((radius - 7.dp).toPx()))) }
    val wall = Brush.linearGradient(
        listOf(Color(0xFF28180F), Color(0xFF573821), Color(0xFFA87D50)), bounds.topLeft, bounds.bottomRight)
    val material = Brush.linearGradient(
        listOf(Color(0xFF795638), Color(0xFF6C4A30), Color(0xFF805C3C)), floorBounds.topLeft, floorBounds.bottomRight)
    val fibres = if (texture == null) woodFibres(bounds, 1.dp.toPx()) else emptyList()
    onDrawBehind {
        drawPath(opening, wall)
        clipPath(opening) {
            if (texture != null) {
                drawImage(texture, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
                drawPath(opening, Brush.linearGradient(listOf(Color(0xFF150A03).copy(alpha = .80f), Color(0xFF211107).copy(alpha = .40f), Color(0xFFFFCD83).copy(alpha = .32f)), bounds.topLeft, bounds.bottomRight))
            } else fibres.forEach { drawPath(it.path, it.color, style = Stroke(it.width)) }
        }
        drawPath(opening, Brush.linearGradient(listOf(Color(0xFF946E49), Color(0xFF58402C), Color(0xFFB79262)),
            bounds.topLeft, bounds.bottomRight), style = Stroke(1.3.dp.toPx()))
        drawPath(floor, material)
        clipPath(floor) {
            if (texture != null) {
                drawImage(texture, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
                drawPath(floor, Brush.linearGradient(listOf(Color(0xFF1A0E05).copy(alpha = .09f), Color.Transparent, Color(0xFFFFCB84).copy(alpha = .04f)), bounds.topLeft, bounds.bottomRight))
            } else fibres.forEach { drawPath(it.path, it.color, style = Stroke(it.width)) }
            // Broad ambient occlusion at the top and left, fading onto the floor.
            for (step in 10 downTo 1) {
                translate(3.dp.toPx(), 4.dp.toPx()) {
                    drawPath(floor, Color(0xFF1D1008).copy(alpha = .06f), style = Stroke(step * 2.2.dp.toPx()))
                }
            }
            repeat(1100) { i ->
                val x = ((i * 73 + 19) % 997) / 997f * size.width
                val y = ((i * 137 + 31) % 991) / 991f * size.height
                drawCircle(if (i % 3 == 0) Color(0xFF23140B).copy(alpha = .12f) else Color(0xFFFFCFA0).copy(alpha = .07f),
                    .24.dp.toPx(), Offset(x, y))
            }
        }
        drawPath(floor, Brush.linearGradient(listOf(Color(0xFF231409).copy(alpha = .9f), Color.Transparent, Color(0xFFE0B37E).copy(alpha = .35f)),
            floorBounds.topLeft, floorBounds.bottomRight), style = Stroke(.7.dp.toPx()))
    }
}

fun Modifier.neuSurface(radius: Dp = 20.dp, recessed: Boolean = false, color: Color = C.Surface, depth: Dp = 5.dp): Modifier = drawWithCache {
    val shape = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius.toPx()))) }
    onDrawBehind { softShape(shape, color, depth.toPx(), recessed) }
}
