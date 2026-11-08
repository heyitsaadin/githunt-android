package com.githunt.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * Material 3 Expressive loading indicator.
 *
 * The official LoadingIndicator only ships in material3 1.5 (still a release candidate, and it
 * needs AGP 9 / compileSdk 37), so this is the same idea built on the current stack: a solid
 * shape that morphs from one shape to the next on a bouncy spring while it slowly turns. Every
 * shape is a radius-per-angle table, so morphing is just blending two tables. When material3 1.5
 * is stable this file can be swapped for the real component in one place.
 */

private const val Samples = 96
private const val HoldMillis = 380L
private val TwoPi = (2.0 * PI).toFloat()

/** Samples a radius function, rounds every corner a little, and scales the largest radius to 1. */
private fun shapeTable(radiusAt: (Float) -> Float): FloatArray {
    var current = FloatArray(Samples) { i -> radiusAt(TwoPi * i / Samples) }
    repeat(2) {
        val source = current
        current = FloatArray(Samples) { i ->
            var sum = 0f
            for (k in -2..2) sum += source[(i + k + Samples) % Samples]
            sum / 5f
        }
    }
    val largest = current.maxOrNull() ?: 1f
    return FloatArray(Samples) { i -> current[i] / largest }
}

private fun polygonRadius(sides: Int, theta: Float): Float {
    val sector = TwoPi / sides
    val local = ((theta % sector) + sector) % sector - sector / 2f
    return cos(PI.toFloat() / sides) / cos(local)
}

private fun superEllipse(theta: Float, a: Float, b: Float, power: Float): Float {
    val c = abs(cos(theta)) / a
    val s = abs(sin(theta)) / b
    return (c.pow(power) + s.pow(power)).pow(-1f / power)
}

private val LoaderShapes: List<FloatArray> by lazy {
    listOf(
        // soft burst
        shapeTable { t -> 0.80f + 0.20f * cos(10f * t) },
        // nine-sided cookie
        shapeTable { t -> 0.90f + 0.10f * cos(9f * t) },
        // rounded pentagon
        shapeTable { t -> polygonRadius(5, t) },
        // pill
        shapeTable { t -> superEllipse(t, 1f, 0.52f, 4f) },
        // sunny
        shapeTable { t ->
            val f = 0.5f + 0.5f * cos(8f * t)
            0.68f + 0.32f * f.pow(1.5f)
        },
        // four-sided cookie
        shapeTable { t -> 0.88f + 0.12f * cos(4f * t - PI.toFloat()) },
        // oval
        shapeTable { t -> superEllipse(t, 1f, 0.72f, 2f) },
    )
}

/**
 * The morphing loading spinner that replaces Material's circular progress arc.
 *
 * With [progress] left null it spins forever (indeterminate). Give it a progress lambda
 * (0..1) and it instead morphs and turns in step with that value, which is how the
 * pull-to-refresh indicator uses it while the finger is still dragging.
 */
@Composable
fun GhLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    diameter: Dp = 40.dp,
    progress: (() -> Float)? = null,
) {
    val shapes = LoaderShapes
    val morph = remember { Animatable(0f) }
    var base by remember { mutableIntStateOf(0) }
    val spin = rememberInfiniteTransition(label = "gh-loader-spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)),
        label = "gh-loader-angle",
    )

    if (progress == null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(HoldMillis)
                // The spring may overshoot past the next shape, which is the bounce.
                morph.animateTo((base + 1).toFloat(), spring(dampingRatio = 0.55f, stiffness = 140f))
                base += 1
            }
        }
    }

    val xs = remember { FloatArray(Samples) }
    val ys = remember { FloatArray(Samples) }
    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val driver = progress
        val position: Float
        val from: Int
        val angle: Float
        if (driver != null) {
            val v = driver().coerceIn(0f, 1f)
            position = v * 2f
            from = position.toInt().coerceAtMost(1)
            angle = v * 270f
        } else {
            from = base
            position = morph.value
            angle = spin.value + position * 40f
        }
        val fraction = position - from
        val count = shapes.size
        val a = shapes[Math.floorMod(from, count)]
        val b = shapes[Math.floorMod(from + 1, count)]

        val radius = this.size.minDimension / 2f * 0.92f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        for (i in 0 until Samples) {
            val r = (a[i] + (b[i] - a[i]) * fraction).coerceAtLeast(0.2f) * radius
            val t = TwoPi * i / Samples
            xs[i] = cx + r * cos(t)
            ys[i] = cy + r * sin(t)
        }
        path.rewind()
        path.moveTo((xs[Samples - 1] + xs[0]) / 2f, (ys[Samples - 1] + ys[0]) / 2f)
        for (i in 0 until Samples) {
            val j = (i + 1) % Samples
            path.quadraticBezierTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
        }
        path.close()
        rotate(angle) { drawPath(path, color) }
    }
}

/** The same loader inside a round container, for loading that sits on its own on a page. */
@Composable
fun GhContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.onSurface,
    diameter: Dp = 48.dp,
    progress: (() -> Float)? = null,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        GhLoadingIndicator(color = indicatorColor, diameter = diameter * 0.66f, progress = progress)
    }
}

/**
 * Pull-to-refresh indicator: a round chip that drops in from the top with the morphing loader
 * inside. While you drag it follows your finger; once refreshing it spins on its own.
 * Use it as the `indicator` of PullToRefreshBox, inside a `Modifier.align(Alignment.TopCenter)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val elevationPx = with(density) { 6.dp.toPx() }
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                val pull = state.distanceFraction
                translationY = pull * thresholdPx - size.height
                val s = pull.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
                alpha = s
                shadowElevation = elevationPx
                shape = CircleShape
                clip = true
            }
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        GhLoadingIndicator(
            color = scheme.onSurface,
            diameter = 32.dp,
            progress = if (isRefreshing) null else ({ state.distanceFraction }),
        )
    }
}
