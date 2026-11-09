package com.githunt.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.githunt.android.ui.theme.GhMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Corner size that springs between [rest] and [pressed] while the component is held down.
 * This is the Material 3 Expressive "shape morph on press": buttons and list rows change
 * their corner radius under your finger and spring back when you let go.
 */
@Composable
fun animatedPressCorner(interactionSource: InteractionSource, rest: Dp, pressed: Dp): Dp {
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) pressed else rest,
        animationSpec = GhMotion.fastSpatial(),
        label = "press-corner",
    )
    // A springy overshoot must never push a corner below zero.
    return corner.coerceAtLeast(0.dp)
}

/** A shape drawn from a radius-per-angle function (1f = touching the edge of the box). */
fun polarShape(samples: Int = 90, radius: (Float) -> Float): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = minOf(size.width, size.height) / 2f
    val twoPi = (2.0 * PI).toFloat()
    val xs = FloatArray(samples)
    val ys = FloatArray(samples)
    for (i in 0 until samples) {
        val t = twoPi * i / samples
        val r = radius(t) * base
        xs[i] = cx + r * cos(t)
        ys[i] = cy + r * sin(t)
    }
    moveTo((xs[samples - 1] + xs[0]) / 2f, (ys[samples - 1] + ys[0]) / 2f)
    for (i in 0 until samples) {
        val j = (i + 1) % samples
        quadraticBezierTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
    }
    close()
}

/** A nine-sided scalloped "cookie", the Expressive alternative to a plain circle for avatars. */
val CookieShape: Shape = polarShape { t -> 0.93f + 0.07f * cos(9f * t) }

/** Switch with an icon in its thumb (a tick when on, a cross when off), as in Material 3 Expressive. */
@Composable
fun GhSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            PathIcon(
                pathData = if (checked) NavIcons.Check else NavIcons.Close,
                tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
                iconSize = SwitchDefaults.IconSize,
            )
        },
    )
}
