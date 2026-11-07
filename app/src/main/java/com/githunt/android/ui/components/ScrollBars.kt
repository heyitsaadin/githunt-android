package com.githunt.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Decides when the top bar and the bottom nav are hidden while a list scrolls.
 *
 *  - Reading on (content moving up): once the list has travelled [hideAfterPx], the bars hide.
 *  - Heading back to the top (content moving down): the bars show again straight away.
 *  - When scrolling stops, see [rememberScrollBarsState]: the bars come back after a short pause.
 *
 * Attach [connection] to the list with `Modifier.nestedScroll(state.connection)`.
 */
@Stable
class ScrollBarsState(private val hideAfterPx: Float) {
    var hidden by mutableStateOf(false)
        private set

    /** Bumped on every scroll movement; the idle timer restarts whenever it changes. */
    var scrollTick by mutableIntStateOf(0)
        private set

    private var travelled = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero
            scrollTick++
            if (dy > 0f) {
                // Finger moving down = going back toward the top of the feed.
                show()
            } else {
                travelled += -dy
                if (travelled > hideAfterPx && !hidden) hidden = true
            }
            return Offset.Zero
        }
    }

    fun show() {
        travelled = 0f
        if (hidden) hidden = false
    }
}

/**
 * @param hideAfter how far the list must scroll forward before the bars hide.
 * @param revealAfterIdleMs how long scrolling must be still before the hidden bars come back.
 */
@Composable
fun rememberScrollBarsState(
    hideAfter: Dp = 48.dp,
    revealAfterIdleMs: Long = 700L,
): ScrollBarsState {
    val hideAfterPx = with(LocalDensity.current) { hideAfter.toPx() }
    val state = remember(hideAfterPx) { ScrollBarsState(hideAfterPx) }
    LaunchedEffect(state.scrollTick) {
        delay(revealAfterIdleMs)
        state.show()
    }
    return state
}
