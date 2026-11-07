package com.githunt.android.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.githunt.android.data.engagement.EngagementEvent
import com.githunt.android.data.engagement.EngagementTracker
import com.githunt.android.data.feed.RepoItem

/** A card on screen this long (seconds) counts as a real look. Same as the website. */
private const val DWELL_SECONDS = 4.0

/**
 * A card that was only on screen for a flick (a fast fling past it) is not "shown and skipped":
 * nobody could have read it, so it sends nothing instead of a negative signal.
 */
private const val MIN_SKIP_SECONDS = 0.7

/**
 * Times how long one card is really in view and reports it once the card leaves, like the
 * website's dwell tracker: 4 seconds or more is a `dwell`, less with no other action is a `skip`.
 * Time with the app in the background does not count. Every call is on the main thread and writes
 * plain fields, so scrolling never recomposes anything because of this.
 */
internal class CardDwell(
    private val tracker: EngagementTracker?,
    private val item: RepoItem,
) {
    private var visible = false
    private var foreground = true

    /** elapsedRealtime when counting started, or 0 while not counting. */
    private var since = 0L

    /** True once a deliberate action (README, Ask AI, GitHub, share, clip) happened during this look. */
    var engaged = false

    fun onVisibility(nowVisible: Boolean) {
        if (nowVisible == visible) return
        visible = nowVisible
        if (nowVisible) startIfCounting() else flush()
    }

    fun onForeground(isForeground: Boolean) {
        if (isForeground == foreground) return
        foreground = isForeground
        if (isForeground) startIfCounting() else flush()
    }

    /** The card left the composition (scrolled far away or removed): report what it has. */
    fun leave() {
        visible = false
        flush()
    }

    private fun startIfCounting() {
        if (visible && foreground && since == 0L) since = SystemClock.elapsedRealtime()
    }

    private fun flush() {
        if (since == 0L) return
        val seconds = (SystemClock.elapsedRealtime() - since) / 1000.0
        since = 0L
        val wasEngaged = engaged
        engaged = false
        when {
            seconds >= DWELL_SECONDS -> tracker?.record(item, EngagementEvent.DWELL, seconds)
            !wasEngaged && seconds >= MIN_SKIP_SECONDS -> tracker?.record(item, EngagementEvent.SKIP, seconds)
        }
    }
}

@Composable
internal fun rememberCardDwell(item: RepoItem, tracker: EngagementTracker?): CardDwell {
    val dwell = remember(item.fullName, tracker) { CardDwell(tracker, item) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dwell.onForeground(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { dwell.onForeground(true) }
    DisposableEffect(dwell) { onDispose { dwell.leave() } }
    return dwell
}

/**
 * Tells [dwell] whether this card is "in view": at least half of the card is inside the window, or
 * (for a card taller than the screen, such as one with its README open) at least half of the
 * window is this card.
 */
@Composable
internal fun Modifier.trackDwell(dwell: CardDwell): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val windowHeight = view.height.toFloat()
        val shown = minOf(bounds.bottom, windowHeight) - maxOf(bounds.top, 0f)
        val inView = bounds.height > 0f && shown > 0f &&
            (shown / bounds.height >= 0.5f || shown >= windowHeight * 0.5f)
        dwell.onVisibility(inView)
    }
}
