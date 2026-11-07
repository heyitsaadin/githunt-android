package com.githunt.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * Decides when the top bar and the bottom nav are hidden while a list scrolls.
 *
 *  - Reading on (content moving up): once the list has moved [hideAfterPx] in one go, the bars hide.
 *  - Heading back up: the bars only come back after a real scroll back of [revealAfterPx], so a
 *    small flick or a bit of jitter does not bring them back.
 *  - Reaching the very top (the list can't scroll back any further) shows them straight away.
 *
 * Attach [connection] to the list with `Modifier.nestedScroll(state.connection)`, and put
 * [collapseModifier] on each bar so it slides and fades away smoothly.
 *
 * Nothing here is written on every scroll frame: [hidden] only changes when a threshold is crossed,
 * and the animation ([progress]) is read in the layout and draw phases only, so scrolling does not
 * cause recompositions.
 */
@Stable
class ScrollBarsState(
    private val hideAfterPx: Float,
    private val revealAfterPx: Float,
) {
    var hidden by mutableStateOf(false)
        private set

    /** 1 = bars fully shown, 0 = fully hidden. Read it inside layout / draw lambdas only. */
    val progress = Animatable(1f)

    // Plain fields (not state): they change on every scroll frame and nobody observes them.
    private var forwardTravel = 0f
    private var backTravel = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            when {
                dy < 0f -> {
                    // Reading on: content is moving up.
                    backTravel = 0f
                    forwardTravel += -dy
                    if (!hidden && forwardTravel > hideAfterPx) hidden = true
                }
                dy > 0f -> {
                    // Heading back toward the top.
                    forwardTravel = 0f
                    if (hidden) {
                        backTravel += dy
                        if (backTravel > revealAfterPx) show()
                    } else {
                        backTravel = 0f
                    }
                }
            }
            // Dragging down with nothing left to scroll: the list is at the very top.
            if (available.y > 0f && dy == 0f) show()
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // A fling that ran into the top of the list.
            if (available.y > 0f) show()
            return Velocity.Zero
        }
    }

    fun show() {
        forwardTravel = 0f
        backTravel = 0f
        if (hidden) hidden = false
    }

    /**
     * Put this on a bar. The bar's height shrinks to nothing (and it fades) as [progress] goes from
     * 1 to 0, and grows back the same way. [slideUp] is true for a top bar (it slides up and away),
     * false for a bottom bar (it slides down and away). [active] lets a caller keep the bar fully
     * shown when the scroll-hide does not apply (for example on another tab).
     */
    fun collapseModifier(slideUp: Boolean, active: () -> Boolean = { true }): Modifier =
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val fraction = if (active()) progress.value.coerceIn(0f, 1f) else 1f
                val height = (placeable.height * fraction).roundToInt()
                layout(placeable.width, height) {
                    placeable.placeRelative(0, if (slideUp) height - placeable.height else 0)
                }
            }
            .graphicsLayer {
                alpha = if (active()) progress.value.coerceIn(0f, 1f) else 1f
            }
}

/**
 * @param hideAfter how far the list must scroll forward before the bars hide.
 * @param revealAfter how far the list must scroll back before the hidden bars come back.
 */
@Composable
fun rememberScrollBarsState(
    hideAfter: Dp = 48.dp,
    revealAfter: Dp = 120.dp,
): ScrollBarsState {
    val density = LocalDensity.current
    val hideAfterPx = with(density) { hideAfter.toPx() }
    val revealAfterPx = with(density) { revealAfter.toPx() }
    val state = remember(hideAfterPx, revealAfterPx) { ScrollBarsState(hideAfterPx, revealAfterPx) }
    LaunchedEffect(state) {
        // One animation drives both bars; it restarts from wherever it is if the direction flips.
        snapshotFlow { state.hidden }.collectLatest { hidden ->
            state.progress.animateTo(
                targetValue = if (hidden) 0f else 1f,
                animationSpec = tween(
                    durationMillis = if (hidden) 240 else 300,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    return state
}
