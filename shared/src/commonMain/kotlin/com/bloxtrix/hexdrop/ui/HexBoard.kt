package com.bloxtrix.hexdrop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlin.math.*

fun computeHexMetrics(w: Float, h: Float): Triple<Float, Float, Float> {
    val radius = min(w / (COLS * 1.5f + 1.1f), h / (sqrt(3f) * (ROWS + 0.9f)))
    return Triple(radius, (w - radius * (COLS * 1.5f + .5f)) / 2,
        (h - sqrt(3f) * radius * (ROWS + .5f)) / 2)
}
fun cellCenter(col: Int, row: Int, hs: Float, ox: Float = 0f, oy: Float = 0f) = Offset(
    ox + col * 1.5f * hs + hs,
    oy + (row + .5f + if (col % 2 == 1) .5f else 0f) * sqrt(3f) * hs)
fun hexPath(cx: Float, cy: Float, r: Float) = Path().apply {
    val points = List(6) { i -> Offset(cx + cos(PI.toFloat() / 3 * i) * r, cy + sin(PI.toFloat() / 3 * i) * r) }
    repeat(6) { i ->
        val corner = points[i]
        val before = corner + (points[(i + 5) % 6] - corner) * .22f
        val after = corner + (points[(i + 1) % 6] - corner) * .22f
        if (i == 0) moveTo(before.x, before.y) else lineTo(before.x, before.y)
        quadraticBezierTo(corner.x, corner.y, after.x, after.y)
    }
    close()
}
fun landingRow(grid: Grid, col: Int) = (ROWS - 1 downTo 0).firstOrNull { grid[it][col] == null } ?: -1

/** Sample one opaque mineral swatch; geometry supplies its thickness and lighting. */
private fun DrawScope.mineralTexture(materials: GameMaterials, cx: Float, cy: Float, r: Float, value: Int, variant: Int) {
    val tier = (value.countTrailingZeroBits() - 1).coerceIn(0, 10)
    // Base atlas: malachite, jasper, (unused ochre), charoite, lapis, turquoise.
    // High atlas: rhodonite, carnelian, howlite, obsidian, tiger's eye, ruby zoisite.
    val atlas = if (tier >= 5) materials.highMinerals else materials.minerals
    val index = when (tier) { 0 -> 0; 1 -> 1; 2 -> 5; 3 -> 3; 4 -> 4; else -> tier - 5 }
    val tileWidth = atlas.width / 2
    val tileHeight = atlas.height / 3
    val sampleWidth = tileWidth * 3 / 4
    val sampleHeight = tileHeight * 3 / 4
    val offsetX = tileWidth / 32 + (variant * 37 % 101) * (tileWidth - sampleWidth - tileWidth / 16) / 101
    val offsetY = tileHeight / 32 + (variant * 61 % 103) * (tileHeight - sampleHeight - tileHeight / 16) / 103
    drawImage(atlas,
        srcOffset = IntOffset((index % 2) * tileWidth + offsetX, (index / 2) * tileHeight + offsetY),
        srcSize = IntSize(sampleWidth, sampleHeight),
        dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
        dstSize = IntSize((r * 2).roundToInt(), (r * 2).roundToInt()), filterQuality = FilterQuality.Medium)
}

/** An opaque, thick mineral piece with a rolled shoulder and a softly honed face. */
fun DrawScope.drawHexCell(cx: Float, cy: Float, hs: Float, value: Int, textMeasurer: TextMeasurer,
    mergeFlash: Float = 0f, glowAlpha: Float = 0f, materials: GameMaterials? = null, textureVariant: Int = 0) {
    val color = C.forValue(value)
    val r = hs * .84f
    val topY = cy - hs * .08f
    val silhouette = hexPath(cx, cy + hs * .12f, r)
    for (step in 7 downTo 1) {
        translate(hs * .025f, hs * .045f) {
            drawPath(silhouette, Color.Black.copy(alpha = .075f), style = Stroke(hs * step * .033f))
        }
    }
    // The vertical edge is the same pigment in shadow, not a transparent overlay.
    drawPath(silhouette, Brush.linearGradient(listOf(lerp(color, Color.Black, .29f), lerp(color, Color.Black, .54f)),
        Offset(cx, cy - r), Offset(cx, cy + r)))
    if (materials != null) clipPath(silhouette) {
        mineralTexture(materials, cx, cy + hs * .12f, r, value, textureVariant)
        drawPath(silhouette, Color.Black.copy(alpha = .46f))
    }
    drawPath(silhouette, Brush.linearGradient(listOf(lerp(color, Color.White, .13f), lerp(color, Color.Black, .50f)),
        Offset(cx - r, cy), Offset(cx + r, cy + r)), style = Stroke(hs * .023f))
    val shoulder = hexPath(cx, topY, r)
    drawPath(shoulder, Brush.linearGradient(
        listOf(lerp(color, Color.White, .40f), lerp(color, Color.White, .12f), color, lerp(color, Color.Black, .32f)),
        Offset(cx - r, topY - r), Offset(cx + r, topY + r)))
    if (materials != null) clipPath(shoulder) {
        mineralTexture(materials, cx, topY, r, value, textureVariant)
        drawPath(shoulder, Brush.linearGradient(listOf(Color.White.copy(alpha = .34f), Color.White.copy(alpha = .07f), Color.Black.copy(alpha = .30f)),
            Offset(cx - r, topY - r), Offset(cx + r, topY + r)))
    }
    val face = hexPath(cx, topY, r * .89f)
    drawPath(face, Brush.linearGradient(listOf(lerp(color, Color.White, .045f), color, lerp(color, Color.Black, .035f)),
        Offset(cx - r * .6f, topY - r), Offset(cx + r * .4f, topY + r)))
    if (materials != null) clipPath(face) {
        mineralTexture(materials, cx, topY, r, value, textureVariant)
        drawPath(face, Brush.linearGradient(listOf(Color.White.copy(alpha = .035f), Color.Transparent, Color.Black.copy(alpha = .055f)),
            Offset(cx - r, topY - r), Offset(cx + r, topY + r)))
    }
    // An outer rolled edge catches the light; the face has no glass-like inset border.
    drawPath(shoulder, Brush.linearGradient(listOf(Color.White.copy(alpha = .38f), Color.Transparent, Color.Black.copy(alpha = .18f)),
        Offset(cx - r, topY - r), Offset(cx + r, topY + r)), style = Stroke(hs * .016f))
    if (materials == null) clipPath(face) {
        // Thin, irregular mineral seams stay within the opaque body of each stone.
        repeat(5) { vein ->
            val veinPath = Path()
            val phase = value.countTrailingZeroBits() * .73f + vein * 1.9f
            for (step in 0..18) {
                val x = cx - r + step / 18f * r * 2
                val y = topY - r + (vein + .5f) / 5f * r * 2 +
                    sin(step * .25f + phase) * r * .16f + sin(step * .57f + phase * 2) * r * .045f
                if (step == 0) veinPath.moveTo(x, y) else veinPath.lineTo(x, y)
            }
            drawPath(veinPath, lerp(color, Color.Black, .30f).copy(alpha = .23f), style = Stroke(hs * .05f))
            drawPath(veinPath, lerp(color, Color.White, .26f).copy(alpha = .33f), style = Stroke(hs * .014f))
        }
        repeat(52) { i ->
            val x = cx - r + ((i * 37 + value * 3) % 101) / 101f * r * 2
            val y = topY - r + ((i * 67 + value) % 103) / 103f * r * 2
            drawCircle(if (i % 3 == 0) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .09f),
                hs * .007f, Offset(x, y))
        }
    }
    val label = if (value >= 1024) "${value / 1024}k" else value.toString()
    val text = textMeasurer.measure(label, TextStyle(color = C.textForValue(value), fontWeight = FontWeight.ExtraBold,
        fontSize = (hs * if (label.length > 2) .53f else .65f).toSp()))
    val labelOrigin = Offset(cx - text.size.width / 2, topY - text.size.height / 2 - r * .035f)
    // The inscription is inlaid, with a tiny dark cut and a light lower edge.
    val ivory = C.textForValue(value).red > .8f
    drawText(text, color = Color.Black.copy(alpha = if (ivory) .8f else .30f), topLeft = labelOrigin + Offset(hs * .017f, hs * .025f))
    if (!ivory) drawText(text, color = Color.White.copy(alpha = .5f), topLeft = labelOrigin + Offset(0f, hs * .026f))
    drawText(text, topLeft = labelOrigin)
    if (mergeFlash > 0) drawPath(shoulder, Color.White.copy(alpha = mergeFlash * .10f))
    if (glowAlpha > 0) drawCircle(color.copy(alpha = glowAlpha * .24f), hs * (1.0f + (1 - glowAlpha) * 1.1f), Offset(cx, cy), style = Stroke(hs * .025f))
}

fun DrawScope.drawSocket(cx: Float, cy: Float, hs: Float, selected: Boolean = false, materials: GameMaterials? = null, woodBounds: Rect? = null) {
    val opening = hexPath(cx, cy, hs * .89f)
    val floor = hexPath(cx + hs * .013f, cy + hs * .055f, hs * .77f)
    val wallLight = if (selected) Color(0xFFC6A16D) else Color(0xFFB88F5B)
    drawPath(opening, Brush.linearGradient(listOf(Color(0xFF29190F), Color(0xFF624129), wallLight),
        Offset(cx - hs * .7f, cy - hs), Offset(cx + hs * .6f, cy + hs)))
    drawPath(opening, Brush.linearGradient(listOf(Color(0xFF48301D), Color(0xFF7E5A36), wallLight),
        Offset(cx - hs, cy - hs), Offset(cx + hs, cy + hs)), style = Stroke(hs * .018f))
    drawPath(floor, Brush.linearGradient(listOf(Color(0xFF473020), Color(0xFF64462D)),
        Offset(cx, cy - hs), Offset(cx, cy + hs)))
    clipPath(floor) {
        if (materials != null) {
            val texture = materials.walnut
            val timber = woodBounds ?: Rect(cx - hs, cy - hs, cx + hs, cy + hs)
            // Grain stays registered with the surrounding slab, even inside the cut.
            drawImage(texture,
                dstOffset = IntOffset(timber.left.roundToInt(), timber.top.roundToInt()),
                dstSize = IntSize(timber.width.roundToInt(), timber.height.roundToInt()), filterQuality = FilterQuality.Medium)
            drawPath(floor, Color(0xFF201006).copy(alpha = .28f))
        } else repeat(8) { i ->
            val x = cx - hs + hs * i * .28f
            drawLine(Color(0xFF21120B).copy(alpha = .15f), Offset(x - hs * .04f, cy - hs), Offset(x + hs * .03f, cy + hs), hs * .009f)
        }
        for (step in 4 downTo 1) {
            translate(hs * .025f, hs * .04f) {
                drawPath(floor, Color.Black.copy(alpha = .09f), style = Stroke(hs * step * .055f))
            }
        }
    }
}
@Composable
fun Crystal(value: Int, modifier: Modifier = Modifier) {
    val measure = rememberTextMeasurer()
    val materials = LocalGameMaterials.current
    Canvas(modifier) { drawHexCell(size.width / 2, size.height / 2, min(size.width, size.height) * .49f, value, measure, materials = materials) }
}

/** Board moments reported to the screen for sound and haptics, in step with the animation. */
sealed interface BoardEvent {
    data object Landed : BoardEvent
    /** [index] is 0 for the first fusion; 1+ are cascade waves. */
    data class Wave(val index: Int, val wave: MergeWave) : BoardEvent
}

private sealed interface Fx {
    data class Drop(val t: Float) : Fx
    data class Cycle(val t: Float) : Fx
    data class Gather(val wave: Int, val t: Float) : Fx
    data class Fuse(val wave: Int, val t: Float) : Fx
    data class Settle(val wave: Int, val t: Float) : Fx
}

private val Settling = CubicBezierEasing(.45f, 0f, .85f, .55f) // accelerates like a falling stone
private fun Float.ease() = FastOutSlowInEasing.transform(this.coerceIn(0f, 1f))

/** For each stone in [settled], the row it fell from in [merged] (gravity keeps column order). */
private fun fallSources(merged: Grid, settled: Grid): Map<HexPos, Int> = buildMap {
    for (col in 0 until COLS) {
        val from = (ROWS - 1 downTo 0).filter { merged[it][col] != null }
        val to = (ROWS - 1 downTo 0).filter { settled[it][col] != null }
        to.forEachIndexed { k, row -> put(HexPos(row, col), from[k]) }
    }
}

/**
 * [animate] is false when the screen re-enters an already played turn; the board then shows the
 * result without replaying movement or sound. Reduced motion shows results immediately.
 */
@Composable
fun HexBoard(state: GameState, reducedMotion: Boolean, animate: Boolean, onSelect: (Int) -> Unit,
    onEvent: (BoardEvent) -> Unit = {}, onAnimating: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    val measure = rememberTextMeasurer()
    val materials = LocalGameMaterials.current
    val selectColumn by rememberUpdatedState(onSelect)
    val emit by rememberUpdatedState(onEvent)
    val busy by rememberUpdatedState(onAnimating)
    var fx by remember { mutableStateOf<Fx?>(null) }
    // The final board of the previous turn, and the column order shown while a Cycle runs.
    var settledGrid by remember { mutableStateOf(state.grid) }
    var previousGrid by remember { mutableStateOf(state.grid) }
    // The last turn whose animation finished (or was skipped); a newer turn is drawn from its start.
    var shownTurn by remember { mutableStateOf(state.turnKey) }
    val chainWord = words("ZİNCİR", "CASCADE")
    LaunchedEffect(state.turnKey) {
        val before = settledGrid
        settledGrid = state.grid
        previousGrid = before
        if (state.turnKey == 0 || !animate) { fx = null; shownTurn = state.turnKey; return@LaunchedEffect }
        val waves = state.fxWaves
        if (reducedMotion) {
            fx = null
            emit(BoardEvent.Landed)
            waves.lastOrNull()?.let { emit(BoardEvent.Wave(waves.lastIndex, it)) }
            shownTurn = state.turnKey
            return@LaunchedEffect
        }
        busy(true)
        try {
            suspend fun play(ms: Int, make: (Float) -> Fx) {
                val clock = Animatable(0f)
                fx = make(0f)
                clock.animateTo(1f, tween(ms, easing = LinearEasing)) { fx = make(value) }
            }
            if (state.lastDropRow >= 0) play(150) { Fx.Drop(it) } else play(380) { Fx.Cycle(it) }
            emit(BoardEvent.Landed)
            waves.forEachIndexed { index, wave ->
                play(170) { Fx.Gather(index, it) }
                emit(BoardEvent.Wave(index, wave))
                play(230) { Fx.Fuse(index, it) }
                if (wave.merged != wave.settled) play(170) { Fx.Settle(index, it) }
            }
        } finally {
            fx = null
            shownTurn = state.turnKey
            busy(false)
        }
    }
    val ghostRow = landingRow(state.grid, state.currentCol)
    val matches = remember(state.grid, state.currentCol, state.current) {
        dropPiece(state.grid, state.currentCol, state.current)?.let { findMergeGroups(it.grid).flatten().toSet() } ?: emptySet()
    }
    Canvas(modifier.semantics { contentDescription = "${COLS} × ${ROWS}" }
        .pointerInput(state.phase) {
            detectTapGestures { p ->
                if (state.phase == GamePhase.Playing) {
                    val (hs, ox, oy) = computeHexMetrics(size.width.toFloat(), size.height.toFloat())
                    val col = ((p.x - ox - hs) / (1.5f * hs)).roundToInt()
                    if (col in 0 until COLS && p.y >= oy && p.y <= size.height - oy) selectColumn(col)
                }
            }
        }) {
        val (hs, ox, oy) = computeHexMetrics(size.width, size.height)
        val timberBounds = Rect(-14.dp.toPx(), -16.dp.toPx(), size.width + 14.dp.toPx(), size.height + 12.dp.toPx())
        fun center(pos: HexPos) = cellCenter(pos.col, pos.row, hs, ox, oy)
        fun stone(p: Offset, value: Int, col: Int, scale: Float = 1f, glow: Float = 0f) =
            drawHexCell(p.x, p.y, hs * scale, value, measure, glowAlpha = glow, materials = materials, textureVariant = col)
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            val p = cellCenter(col, row, hs, ox, oy)
            drawSocket(p.x, p.y, hs, col == state.currentCol, materials, timberBounds)
        }
        // Until the effect starts, a fresh turn shows its first frame, never the final cascade result.
        val pending = fx == null && animate && !reducedMotion && state.turnKey > 0 && state.turnKey != shownTurn
        val current = fx ?: if (pending) (if (state.lastDropRow >= 0) Fx.Drop(0f) else Fx.Cycle(0f)) else null
        val cycleBase = if (pending) settledGrid else previousGrid
        val start = state.fxStart ?: state.grid
        when (current) {
            null -> {
                for (row in 0 until ROWS) for (col in 0 until COLS) {
                    val value = state.grid[row][col] ?: continue
                    val pos = HexPos(row, col)
                    stone(center(pos), value, col)
                    if (pos in matches) drawPath(hexPath(center(pos).x, center(pos).y, hs * .96f), C.Accent, style = Stroke(1.25.dp.toPx()))
                }
            }
            is Fx.Drop -> {
                val landing = HexPos(state.lastDropRow, state.lastDropCol)
                for (row in 0 until ROWS) for (col in 0 until COLS) {
                    val value = start[row][col] ?: continue
                    if (HexPos(row, col) != landing) stone(cellCenter(col, row, hs, ox, oy), value, col)
                }
                val destination = center(landing)
                val top = cellCenter(landing.col, 0, hs, ox, oy)
                val y = top.y + (destination.y - top.y) * Settling.transform(current.t)
                val p = Offset(destination.x, y)
                drawLine(Brush.verticalGradient(listOf(Color.Transparent, C.forValue(state.lastDropValue).copy(alpha = .16f)),
                    startY = max(top.y, y - hs * 2), endY = y + 1), Offset(p.x, max(top.y, y - hs * 2)), p, hs * .66f, StrokeCap.Round)
                stone(p, state.lastDropValue, landing.col)
            }
            is Fx.Cycle -> {
                val col = state.currentCol
                for (row in 0 until ROWS) for (c in 0 until COLS) {
                    val value = cycleBase[row][c] ?: continue
                    if (c != col) stone(cellCenter(c, row, hs, ox, oy), value, c)
                }
                val occupied = (0 until ROWS).filter { cycleBase[it][col] != null }
                if (occupied.isNotEmpty()) {
                    val progress = current.t.ease()
                    for (row in occupied.dropLast(1)) {
                        val from = cellCenter(col, row, hs, ox, oy)
                        stone(Offset(from.x, from.y + sqrt(3f) * hs * progress), cycleBase[row][col]!!, col)
                    }
                    val from = cellCenter(col, occupied.last(), hs, ox, oy)
                    val to = cellCenter(col, occupied.first(), hs, ox, oy)
                    val direction = if (col == COLS - 1) -1f else 1f
                    val arc = sin(progress * PI.toFloat())
                    stone(Offset(from.x + arc * hs * 1.35f * direction, from.y + (to.y - from.y) * progress),
                        cycleBase[occupied.last()][col]!!, col, 1f + arc * .08f, glow = arc * .6f)
                }
            }
            is Fx.Gather -> {
                val wave = state.fxWaves[current.wave]
                val moving = wave.groups.flatMap { it.cells }.toSet()
                for (row in 0 until ROWS) for (col in 0 until COLS) {
                    val value = wave.before[row][col] ?: continue
                    if (HexPos(row, col) !in moving) stone(cellCenter(col, row, hs, ox, oy), value, col)
                }
                val t = current.t.ease()
                for (group in wave.groups) {
                    val target = center(group.target)
                    val value = group.value / 2
                    // The fusing stones glow, then slide into the surviving socket.
                    drawCircle(C.forValue(value).copy(alpha = .22f * t), hs * (1.1f + .5f * t), target)
                    for (cell in group.cells - group.target) {
                        val from = center(cell)
                        stone(from + (target - from) * t, value, cell.col, 1f - .18f * t, glow = .5f)
                    }
                    stone(target, value, group.target.col, 1f, glow = .4f + .6f * t)
                }
            }
            is Fx.Fuse -> {
                val wave = state.fxWaves[current.wave]
                val targets = wave.groups.associateBy { it.target }
                for (row in 0 until ROWS) for (col in 0 until COLS) {
                    val value = wave.merged[row][col] ?: continue
                    val pos = HexPos(row, col)
                    if (pos !in targets) stone(cellCenter(col, row, hs, ox, oy), value, col)
                }
                val t = current.t
                for (group in wave.groups) {
                    val p = center(group.target)
                    val color = C.forValue(group.value)
                    // A short stone "set": swell, settle, ring and mineral chips; never a transparent stone.
                    drawCircle(color.copy(alpha = (1 - t) * .5f), hs * (1f + t * 1.25f), p, style = Stroke(hs * .07f * (1 - t) + 1f))
                    repeat(10) { i ->
                        val a = i * PI.toFloat() / 5 + group.target.col * .7f
                        val d = hs * (.7f + t.ease() * 1.15f)
                        val chip = hexPath(p.x + cos(a) * d, p.y + sin(a) * d + t * t * hs * .5f, hs * .085f * (1 - t * .7f))
                        drawPath(chip, lerp(color, Color.White, .15f).copy(alpha = 1 - t))
                    }
                    stone(p, group.value, group.target.col, 1f + sin(t * PI.toFloat()) * .14f, glow = 1 - t)
                }
                val score = "+${wave.score}"
                val anchor = wave.groups.first().target.let(::center)
                val label = measure.measure(score, TextStyle(color = C.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = (hs * .6f).toSp()))
                val rise = hs * (1.1f + t * .9f)
                drawText(label, color = Color.Black.copy(alpha = .55f * (1 - t * t)), topLeft = Offset(anchor.x - label.size.width / 2 + 2f, anchor.y - rise + 2f))
                drawText(label, color = C.Gold.copy(alpha = 1 - t * t), topLeft = Offset(anchor.x - label.size.width / 2, anchor.y - rise))
                if (current.wave >= 1) {
                    val banner = measure.measure("$chainWord ×${current.wave + 1}",
                        TextStyle(fontWeight = FontWeight.Black, fontSize = (hs * .8f).toSp(), letterSpacing = (hs * .07f).toSp()))
                    val bp = Offset(size.width / 2 - banner.size.width / 2, size.height * .14f - t * hs * .3f)
                    val fade = if (t < .2f) t / .2f else 1f - (t - .2f) / .8f * .6f
                    // A dark walnut-toned plate keeps the label readable over stones and grain.
                    drawRoundRect(Color(0xFF1B120B).copy(alpha = .62f * fade), bp - Offset(hs * .35f, hs * .12f),
                        Size(banner.size.width + hs * .7f, banner.size.height + hs * .24f), CornerRadius(hs * .3f))
                    drawText(banner, color = Color.Black.copy(alpha = .5f * fade), topLeft = bp + Offset(2f, 3f))
                    drawText(banner, color = C.Accent.copy(alpha = fade), topLeft = bp)
                }
            }
            is Fx.Settle -> {
                val wave = state.fxWaves[current.wave]
                val sources = fallSources(wave.merged, wave.settled)
                val t = Settling.transform(current.t)
                for ((pos, fromRow) in sources) {
                    val value = wave.settled[pos.row][pos.col] ?: continue
                    val to = center(pos)
                    val from = cellCenter(pos.col, fromRow, hs, ox, oy)
                    stone(from + (to - from) * t, value, pos.col)
                }
            }
        }
        if (current == null && ghostRow >= 0 && state.phase == GamePhase.Playing) {
            val p = cellCenter(state.currentCol, ghostRow, hs, ox, oy)
            // A landing marker engraved in the empty socket, never a translucent stone.
            drawPath(hexPath(p.x, p.y, hs * .70f), C.forValue(state.current), style = Stroke(1.5.dp.toPx()))
            val txt = measure.measure(state.current.toString(), TextStyle(color = C.TextPrimary.copy(alpha = .85f), fontSize = (hs * .55f).toSp(), fontWeight = FontWeight.Bold))
            drawText(txt, topLeft = Offset(p.x - txt.size.width / 2, p.y - txt.size.height / 2))
        }
    }
}
