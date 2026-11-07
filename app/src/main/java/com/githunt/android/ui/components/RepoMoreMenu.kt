package com.githunt.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
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

// The popup window clips whatever is outside it, so the card sits inside a transparent margin
// that leaves room for its shadow.
private val ShadowMargin = 12.dp

/**
 * The "..." button on a repo card and the menu it opens.
 *
 * The menu is our own popup instead of Material's DropdownMenu: it grows out of the button's corner
 * with a soft spring and a fade, and shrinks back into it when it closes, and it has squircle
 * corners with no square backing behind them.
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

    var open by remember { mutableStateOf(false) }
    // Keeps the popup in the tree until its closing animation has finished.
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open

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
                .clickable(onClickLabel = "More options") { open = true },
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

        if (visible.currentState || visible.targetState) {
            Popup(
                // Top-right corner of the menu sits just under the button's top-right corner.
                alignment = Alignment.TopEnd,
                offset = with(density) { IntOffset(ShadowMargin.roundToPx(), (40.dp - ShadowMargin).roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = scaleIn(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                        initialScale = 0.72f,
                        transformOrigin = TransformOrigin(0.92f, 0.04f),
                    ) + slideInVertically(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                        initialOffsetY = { -it / 10 },
                    ) + fadeIn(tween(durationMillis = 140)),
                    exit = scaleOut(
                        animationSpec = tween(durationMillis = 150),
                        targetScale = 0.8f,
                        transformOrigin = TransformOrigin(0.92f, 0.04f),
                    ) + slideOutVertically(
                        animationSpec = tween(durationMillis = 150),
                        targetOffsetY = { -it / 14 },
                    ) + fadeOut(tween(durationMillis = 120)),
                ) {
                    Box(Modifier.padding(ShadowMargin)) {
                        Surface(
                            shape = SquircleShape(26.dp),
                            color = scheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 12.dp,
                            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 210.dp)
                                    .padding(8.dp),
                            ) {
                                MenuRow(label = "Open on GitHub", onClick = { choose(action = onOpenRepo) })
                                MenuRow(label = "View owner's profile", onClick = { choose(action = onOpenOwner) })
                                if (onNotInterested != null) {
                                    Spacer(Modifier.height(6.dp))
                                    // The one destructive-ish choice: its own grey button.
                                    MenuRow(
                                        label = "Not interested",
                                        grey = true,
                                        onClick = { choose(waitMs = 170L, action = onNotInterested) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, grey: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val shape = SquircleShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (grey) Modifier.background(scheme.onSurface.copy(alpha = 0.12f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}
