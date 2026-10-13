package com.bloxtrix.hexdrop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloxtrix.hexdrop.generated.resources.Res
import com.bloxtrix.hexdrop.generated.resources.senior_oz_cheer
import com.bloxtrix.hexdrop.generated.resources.senior_oz_point
import com.bloxtrix.hexdrop.generated.resources.senior_oz_stones
import org.jetbrains.compose.resources.imageResource
import com.bloxtrix.hexdrop.ui.theme.HexColors as C
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Senior Oz, the OzGAMES mascot: a wise little coral dragon who keeps the stones company. */
enum class OzPose { Point, Stones, Cheer }

@Composable
private fun ozImage(pose: OzPose) = imageResource(when (pose) {
    OzPose.Point -> Res.drawable.senior_oz_point
    OzPose.Stones -> Res.drawable.senior_oz_stones
    OzPose.Cheer -> Res.drawable.senior_oz_cheer
})

/** Decorative; screen readers skip it. Rises in once, then breathes gently unless motion is reduced. */
@Composable
fun SeniorOz(pose: OzPose, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = .62f, stiffness = Spring.StiffnessLow)) }
    val idle = rememberInfiniteTransition(label = "oz-idle")
    val bob by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val sway by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    Image(ozImage(pose), contentDescription = null, modifier = modifier.clearAndSetSemantics { }.graphicsLayer {
        val e = enter.value
        val calm = if (reducedMotion) 0f else 1f
        alpha = e.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(.5f, 1f)
        translationY = (1f - e) * 36.dp.toPx() - bob * 5.dp.toPx() * calm
        rotationZ = sway * 1.6f * calm
        scaleY = 1f + bob * .012f * calm
        scaleX = .9f + .1f * e
    })
}

/** One celebration request; a new [id] replays the entrance. */
data class OzCheer(val id: Long, val text: String, val holdMs: Long = 1300)

/**
 * Senior Oz pops up from the lower-left corner of the board, cheers with a short line and a burst of
 * golden sparkles, then ducks away. It never takes input, so play is never blocked.
 */
@Composable
fun OzCelebration(cheer: OzCheer?, reducedMotion: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 150.dp) {
    if (cheer == null) return
    val show = remember(cheer.id) { Animatable(0f) }
    val sparkle = remember(cheer.id) { Animatable(0f) }
    var done by remember(cheer.id) { mutableStateOf(false) }
    LaunchedEffect(cheer.id) {
        if (reducedMotion) {
            show.snapTo(1f); kotlinx.coroutines.delay(1500); show.animateTo(0f, tween(220))
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { sparkle.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
                show.animateTo(1f, spring(dampingRatio = .5f, stiffness = 420f))
                kotlinx.coroutines.delay(cheer.holdMs)
                show.animateTo(0f, tween(320, easing = FastOutLinearInEasing))
            }
        }
        done = true
    }
    if (done) return
    val v = show.value
    Box(modifier.clearAndSetSemantics { }) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer {
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * 60.dp.toPx()
            translationX = (1f - v) * -24.dp.toPx()
        }) {
            Box(Modifier.size(size), contentAlignment = Alignment.BottomCenter) {
                if (!reducedMotion) Sparkles(sparkle.value, Modifier.matchParentSize())
                Image(ozImage(OzPose.Cheer), contentDescription = null, modifier = Modifier.height(size * .96f)
                    .graphicsLayer { transformOrigin = TransformOrigin(.5f, 1f); val s = .85f + .15f * v; scaleX = s; scaleY = s })
            }
            Text(cheer.text, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = size * .66f)
                    .background(C.Background.copy(alpha = .9f), RoundedCornerShape(14.dp))
                    .border(1.dp, C.Gold.copy(alpha = .55f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

/** Small four-point golden stars flying outward and fading. */
@Composable
private fun Sparkles(t: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .5f, size.height * .45f)
        val fade = (1f - t).coerceIn(0f, 1f)
        repeat(9) { i ->
            val a = (i * 40f + 15f) * PI.toFloat() / 180f
            val d = size.minDimension * (.18f + .42f * t) * (if (i % 2 == 0) 1f else .8f)
            val p = c + Offset(cos(a), -kotlin.math.abs(sin(a)) * 1.1f) * d
            val r = (if (i % 3 == 0) 6.dp else 4.dp).toPx() * (0.6f + 0.4f * fade)
            val star = Path().apply {
                moveTo(p.x, p.y - r); lineTo(p.x + r * .28f, p.y - r * .28f); lineTo(p.x + r, p.y)
                lineTo(p.x + r * .28f, p.y + r * .28f); lineTo(p.x, p.y + r); lineTo(p.x - r * .28f, p.y + r * .28f)
                lineTo(p.x - r, p.y); lineTo(p.x - r * .28f, p.y - r * .28f); close()
            }
            drawPath(star, Color(0xFFFFD27A).copy(alpha = fade * .95f))
        }
    }
}
