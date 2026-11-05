package com.githunt.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance

/**
 * Sweeps one soft highlight across everything drawn inside this composable.
 *
 * Put it on a container whose children are plain grey shapes. The container is drawn into its own
 * layer and the highlight is blended only onto pixels that already exist (SrcAtop), so the gaps
 * between shapes stay clean and every shape in the group lights up in one coherent sweep. The
 * animation value is only read while drawing, so the sweep never recomposes anything.
 */
fun Modifier.shimmerSweep(durationMillis: Int = 1400): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    // A white sheen reads well on light grey; on dark surfaces it has to be much fainter.
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val sheen = Color.White.copy(alpha = if (isLight) 0.65f else 0.10f)
    val clear = sheen.copy(alpha = 0f)

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = size.width * 0.55f
            val start = -band + progress.value * (size.width + band)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(clear, sheen, clear),
                    startX = start,
                    endX = start + band,
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

/** A grey box with its own shimmer sweep. Used behind images while they load. */
@Composable
fun ShimmerBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shimmerSweep()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
