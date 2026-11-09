package com.githunt.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** A light haptic tick for taps, switches and selections. Call the returned function on click. */
@Composable
fun rememberTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        val tick: () -> Unit = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        tick
    }
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

/** Switch with an icon in its thumb (a tick when on, a cross when off), plus a haptic tick. */
@Composable
fun GhSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberTick()
    Switch(
        checked = checked,
        onCheckedChange = if (onCheckedChange == null) {
            null
        } else {
            { value ->
                tick()
                onCheckedChange(value)
            }
        },
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

/**
 * Connected button group (Material 3 Expressive): a row of joined segments with a 2dp gap. The
 * chosen segment is a full pill; the others keep small inner corners and only round at the ends
 * of the row. Pressing a segment rounds it too, all on springs, with a haptic tick on select.
 */
@Composable
fun <T> GhSegmentedPicker(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberTick()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val round = isSelected || pressed
            val start by animateDpAsState(
                targetValue = if (round || index == 0) 26.dp else 6.dp,
                animationSpec = GhMotion.fastSpatial(),
                label = "segment-start",
            )
            val end by animateDpAsState(
                targetValue = if (round || index == items.lastIndex) 26.dp else 6.dp,
                animationSpec = GhMotion.fastSpatial(),
                label = "segment-end",
            )
            val container by animateColorAsState(
                targetValue = if (isSelected) scheme.primary else scheme.surfaceVariant,
                animationSpec = GhMotion.defaultEffects(),
                label = "segment-container",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                animationSpec = GhMotion.defaultEffects(),
                label = "segment-content",
            )
            val s = start.coerceAtLeast(0.dp)
            val e = end.coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = s, topEnd = e, bottomEnd = e, bottomStart = s))
                    .background(container)
                    .selectable(
                        selected = isSelected,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSelected) tick()
                            onSelect(item)
                        },
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
