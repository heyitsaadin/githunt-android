package com.githunt.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Holds whatever full-screen overlay is currently showing above the whole app. */
class PreviewHostState {
    var content: (@Composable () -> Unit)? by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** Null outside a [PreviewHost] (previews, tests): callers then fall back to a Dialog. */
val LocalPreviewHost = staticCompositionLocalOf<PreviewHostState?> { null }

/**
 * Wraps the app and draws full-screen overlays (the image viewer) in the activity's own window,
 * above everything including the bottom navigation. The activity is edge to edge, so an overlay
 * here fills the real screen, status bar and navigation bar included. A separate Dialog window
 * could not be relied on to do that on every phone, which is what let the app behind show
 * through the bar areas.
 */
@Composable
fun PreviewHost(content: @Composable () -> Unit) {
    val state = remember { PreviewHostState() }
    CompositionLocalProvider(LocalPreviewHost provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            val overlay = state.content
            if (overlay != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Nothing under the overlay may react to touches that land on it.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    overlay()
                }
            }
        }
    }
}
