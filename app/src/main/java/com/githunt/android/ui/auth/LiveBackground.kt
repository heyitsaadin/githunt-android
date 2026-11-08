package com.githunt.android.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Login / sign-up palette. Fixed colours: the screen looks the same in light and dark mode.
internal val LavBgTop = Color(0xFFF8F7FD)
internal val LavBgBottom = Color(0xFFF1F0F8)
internal val LavField = Color(0xFFF0EFF5)
internal val LavHairline = Color(0xFFE3E2EE)
internal val LavShadow = Color(0x4D7D7AB8)
private val LavBlob = Color(0xFFDEDCEE)
private val LavSparkle = Color(0xFFB3B2D5)

/** One sparkle. Positions are measured from the design, as fractions of the screen width and height. */
private data class LiveSparkle(
    val x: Float,
    val y: Float,
    val radius: Float,   // fraction of screen width
    val phase: Float,    // 0..1 offset inside the twinkle cycle
    val floatAmp: Float, // fraction of screen width
    val spin: Float,     // degrees of wobble
)

private val liveSparkles = listOf(
    LiveSparkle(0.880f, 0.101f, 0.012f, 0.10f, 0.004f, 8f),
    LiveSparkle(0.800f, 0.131f, 0.047f, 0.00f, 0.010f, 10f),
    LiveSparkle(0.701f, 0.163f, 0.014f, 0.35f, 0.005f, 12f),
    LiveSparkle(0.170f, 0.252f, 0.033f, 0.55f, 0.008f, 8f),
    LiveSparkle(0.245f, 0.284f, 0.014f, 0.80f, 0.005f, 12f),
    LiveSparkle(0.892f, 0.331f, 0.019f, 0.25f, 0.006f, 10f),
    LiveSparkle(0.855f, 0.356f, 0.012f, 0.65f, 0.004f, 8f),
    LiveSparkle(0.709f, 0.920f, 0.011f, 0.45f, 0.004f, 10f),
    LiveSparkle(0.525f, 0.954f, 0.017f, 0.90f, 0.006f, 10f),
)

private data class LiveParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val cycles: Int,
    val sway: Float,
    val phase: Float,
)

/**
 * Animated lavender backdrop:
 *  - big soft blobs that slowly drift and breathe
 *  - four-point sparkles that twinkle, float and wobble
 *  - the little curved arrow that keeps drawing itself
 *  - tiny dust particles drifting upward
 *
 * Every animated value is read inside the draw phase, so nothing here causes recomposition.
 */
@Composable
fun LiveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveBg")

    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val twinkle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle",
    )
    val dust = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "dust",
    )
    val arcProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes<Float> {
                durationMillis = 5200
                0f at 0
                1f at 2600 using FastOutSlowInEasing
                1f at 5200
            },
        ),
        label = "arcProgress",
    )
    val arcAlpha = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes<Float> {
                durationMillis = 5200
                0f at 0
                0.65f at 500
                0.65f at 3800
                0f at 5200
            },
        ),
        label = "arcAlpha",
    )

    val particles = remember {
        val random = Random(11)
        List(16) {
            LiveParticle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.003f + random.nextFloat() * 0.004f,
                cycles = 1 + random.nextInt(3),
                sway = 0.01f + random.nextFloat() * 0.02f,
                phase = random.nextFloat(),
            )
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val d = drift.value
        val breathe = sin(d * PI.toFloat()) // 0 -> 1 -> 0

        // Blobs
        drawCircle(
            LavBlob.copy(alpha = 0.55f), w * (0.40f + 0.03f * breathe),
            Offset(w * (-0.02f + 0.05f * d), h * (-0.01f + 0.015f * d)),
        )
        drawCircle(
            LavBlob.copy(alpha = 0.35f), w * (0.58f + 0.03f * breathe),
            Offset(w * (-0.10f + 0.06f * d), h * (0.02f + 0.02f * d)),
        )
        drawCircle(
            LavBlob.copy(alpha = 0.30f), w * (0.62f - 0.03f * breathe),
            Offset(w * (1.22f - 0.07f * d), h * (0.30f + 0.03f * d)),
        )
        drawCircle(
            LavBlob.copy(alpha = 0.55f), w * (0.55f + 0.03f * breathe),
            Offset(w * (1.05f - 0.06f * d), h * (1.02f - 0.02f * d)),
        )
        drawCircle(
            LavBlob.copy(alpha = 0.40f), w * (0.42f - 0.02f * breathe),
            Offset(w * (0.78f + 0.05f * d), h * (1.07f - 0.015f * d)),
        )

        // Dust drifting upward, looping seamlessly
        particles.forEach { p ->
            val t = dust.value * p.cycles
            val py = (((p.y - t) % 1f) + 1f) % 1f
            val px = p.x + sin((t + p.phase) * 2f * PI.toFloat()) * p.sway
            val fade = sin(py * PI.toFloat())
            drawCircle(LavSparkle.copy(alpha = 0.35f * fade), p.size * w, Offset(px * w, py * h))
        }

        // Self-drawing arrow
        drawArrow(w, h, arcProgress.value, arcAlpha.value)

        // Sparkles
        liveSparkles.forEach { s ->
            val cycle = (twinkle.value + s.phase) % 1f
            val wave = sin(cycle * 2f * PI.toFloat()) // -1..1
            val pulse = 0.5f + 0.5f * wave           // 0..1
            val radius = s.radius * w * (0.72f + 0.38f * pulse)
            val cx = s.x * w + cos(cycle * 2f * PI.toFloat()) * s.floatAmp * w
            val cy = s.y * h + wave * s.floatAmp * w * 1.6f
            rotate(wave * s.spin, Offset(cx, cy)) {
                drawPath(sparklePath(cx, cy, radius), LavSparkle.copy(alpha = 0.35f + 0.55f * pulse))
            }
        }
    }
}

private fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val k = r * 0.16f
    moveTo(cx, cy - r)
    quadraticBezierTo(cx + k, cy - k, cx + r, cy)
    quadraticBezierTo(cx + k, cy + k, cx, cy + r)
    quadraticBezierTo(cx - k, cy + k, cx - r, cy)
    quadraticBezierTo(cx - k, cy - k, cx, cy - r)
    close()
}

private fun DrawScope.drawArrow(w: Float, h: Float, progress: Float, alpha: Float) {
    if (progress <= 0.001f || alpha <= 0.001f) return
    val path = Path().apply {
        moveTo(w * 0.042f, h * 0.268f)
        cubicTo(w * 0.055f, h * 0.205f, w * 0.150f, h * 0.178f, w * 0.238f, h * 0.174f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val end = measure.length * progress
    val segment = Path()
    measure.getSegment(0f, end, segment, true)

    val color = LavSparkle.copy(alpha = alpha * 0.55f)
    val stroke = Stroke(width = w * 0.0045f, cap = StrokeCap.Round)
    drawPath(segment, color, style = stroke)

    // Arrow head follows the tip of the line.
    val tip = measure.getPosition(end)
    val tangent = measure.getTangent(end)
    val head = w * 0.022f
    val angle = Math.toRadians(28.0)
    fun wing(sign: Double): Offset {
        val bx = -tangent.x.toDouble()
        val by = -tangent.y.toDouble()
        val rx = bx * cos(angle * sign) - by * sin(angle * sign)
        val ry = bx * sin(angle * sign) + by * cos(angle * sign)
        return Offset(tip.x + (rx * head).toFloat(), tip.y + (ry * head).toFloat())
    }
    drawLine(color, tip, wing(1.0), stroke.width, StrokeCap.Round)
    drawLine(color, tip, wing(-1.0), stroke.width, StrokeCap.Round)
}
