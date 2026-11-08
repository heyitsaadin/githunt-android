package com.githunt.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A rounded rectangle with "continuous" (squircle) corners, like iOS: each corner starts further
 * along the edge and bends in more gently than a plain circular arc, so it reads softer and rounder
 * than [androidx.compose.foundation.shape.RoundedCornerShape] with the same radius.
 */
internal class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        // How far along each edge the curve starts, and how tightly it hugs the corner.
        val k = minOf(with(density) { radius.toPx() } * 1.35f, w / 2f, h / 2f)
        val a = k * 0.22f
        val path = Path().apply {
            moveTo(k, 0f)
            lineTo(w - k, 0f)
            cubicTo(w - a, 0f, w, a, w, k)
            lineTo(w, h - k)
            cubicTo(w, h - a, w - a, h, w - k, h)
            lineTo(k, h)
            cubicTo(a, h, 0f, h - a, 0f, h - k)
            lineTo(0f, k)
            cubicTo(0f, a, a, 0f, k, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// Menu size. The width is fixed on purpose: a popup window has no width of its own, so rows that
// fill their width would otherwise stretch across the whole screen.
private val MenuWidth = 176.dp
private val MenuRadius = 22.dp

// The popup window clips whatever is outside it, so the card sits inside a transparent margin
// that leaves room for its shadow.
private val ShadowMargin = 10.dp

// Where the menu grows from and shrinks back to: the "..." button's corner.
private val MenuOrigin = TransformOrigin(1f, 0f)

// Material's "emphasized decelerate": fast start, long soft landing.
private val OpenEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val OPEN_MS = 320
private const val CLOSE_MS = 150

/** How far through the open animation row [index] starts to appear, so the rows follow one another. */
private const val ROW_STAGGER = 0.14f

/**
 * The "..." button on a repo card and the menu it opens.
 *
 * The menu is our own popup instead of Material's DropdownMenu, so it has squircle corners with no
 * square backing behind them.
 *
 * Why the animation is built the way it is: opening a popup creates a whole new window, and that
 * first frame is slow. An animation that starts during it loses its first few frames and looks like
 * it jumps. So the menu stays fully transparent until the window has drawn once, and only then
 * eases in (one long ease-out, with the rows appearing one after another). All of it is drawn
 * through graphicsLayer, so nothing is recomposed while it plays.
 *
 * @param onOpenRepo opens the repository on GitHub.
 * @param onOpenOwner opens the owner's profile.
 * @param onNotInterested null hides the row (cards outside the Home feed).
 */
@Composable
internal fun RepoMoreMenu(
    onOpenRepo: () -> Unit,
    onOpenOwner: () -> Unit,
    onNotInterested: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // `open` is what the person asked for; `mounted` keeps the popup window alive until the
    // closing animation has finished.
    var open by remember { mutableStateOf(false) }
    var mounted by remember { mutableStateOf(false) }

    // Closes the menu, then runs the choice. A short wait lets the closing animation play first
    // when the choice removes the card (Not interested).
    fun choose(waitMs: Long = 0L, action: () -> Unit) {
        open = false
        scope.launch {
            if (waitMs > 0L) delay(waitMs)
            action()
        }
    }

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "More options") {
                    open = true
                    mounted = true
                },
            contentAlignment = Alignment.Center,
        ) {
            val dot = scheme.onSurfaceVariant
            Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
                val radius = size.height / 2f
                listOf(radius, size.width / 2f, size.width - radius).forEach { x ->
                    drawCircle(color = dot, radius = radius, center = Offset(x, radius))
                }
            }
        }

        if (mounted) {
            Popup(
                // The menu's top-right corner sits just under the button's top-right corner.
                alignment = Alignment.TopEnd,
                offset = with(density) {
                    IntOffset(ShadowMargin.roundToPx(), (38.dp - ShadowMargin).roundToPx())
                },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // 0 = closed, 1 = fully open. Only ever read inside graphicsLayer lambdas.
                val progress = remember { Animatable(0f) }
                LaunchedEffect(open) {
                    if (open) {
                        // Let the new window draw its first (slow) frame while still invisible.
                        withFrameNanos { }
                        progress.animateTo(1f, tween(durationMillis = OPEN_MS, easing = OpenEasing))
                    } else {
                        progress.animateTo(0f, tween(durationMillis = CLOSE_MS, easing = FastOutLinearInEasing))
                        mounted = false
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(ShadowMargin)
                        .graphicsLayer {
                            val p = progress.value
                            val s = 0.84f + 0.16f * p
                            scaleX = s
                            scaleY = s
                            alpha = (p * 2.2f).coerceAtMost(1f)
                            transformOrigin = MenuOrigin
                        },
                ) {
                    Surface(
                        shape = SquircleShape(MenuRadius),
                        // In dark mode the card and a same-coloured menu merge together, so the menu is
                        // a clearly lighter grey there; in light mode it is only a touch greyer.
                        color = lerp(
                            scheme.surface,
                            scheme.onSurface,
                            if (scheme.surface.luminance() < 0.5f) 0.16f else 0.05f,
                        ),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.4f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(MenuWidth)
                                .padding(6.dp),
                        ) {
                            MenuRow(
                                label = "Open on GitHub",
                                index = 0,
                                progress = { progress.value },
                                onClick = { choose(action = onOpenRepo) },
                            )
                            MenuRow(
                                label = "View owner's profile",
                                index = 1,
                                progress = { progress.value },
                                onClick = { choose(action = onOpenOwner) },
                            )
                            if (onNotInterested != null) {
                                // A thin line, then the one negative choice in grey text. No fill:
                                // a filled row reads as "selected".
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .height(1.dp)
                                        .background(scheme.outline.copy(alpha = 0.3f)),
                                )
                                MenuRow(
                                    label = "Not interested",
                                    index = 2,
                                    progress = { progress.value },
                                    color = scheme.onSurfaceVariant,
                                    onClick = { choose(waitMs = 160L, action = onNotInterested) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row. While the menu opens it fades in and settles up a few dp, a little after the row above
 * it ([ROW_STAGGER] per row), which is what makes the opening read as fluid instead of a pop.
 */
@Composable
private fun MenuRow(
    label: String,
    index: Int,
    progress: () -> Float,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = ((progress() - index * ROW_STAGGER) / 0.5f).coerceIn(0f, 1f)
                alpha = t
                translationY = (1f - t) * 6.dp.toPx()
            }
            .clip(SquircleShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}
