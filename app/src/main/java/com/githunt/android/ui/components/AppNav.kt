package com.githunt.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.githunt.android.ui.theme.GhMotion

/** The three places the signed-in app lives, same as the website's dock: Feed, Discover, Profile. */
enum class AppTab(val label: String, val icon: String) {
    Home("Home", NavIcons.Home),
    Discover("Search", NavIcons.Search),
    Profile("Profile", NavIcons.Person),
}

// Material's own navigation bar proportions: an 80dp bar, a 64 x 32 pill, icon over label.
private val NavHeight = 80.dp
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private val IndicatorTop = 12.dp

/**
 * Flat, full-width bottom bar in the standard Material 3 style (like F-Droid's): three equal
 * tabs, icon over label, and a wide pill behind the active icon.
 *
 * The expressive part is how it moves: the pill stretches as it travels (the edge it is moving
 * towards runs ahead of the other one, then they catch up with a bounce), the active icon pops,
 * and a press squeezes the icon.
 *
 * It sits below the content in a Column (nothing scrolls underneath it), so no screen needs
 * extra bottom padding.
 */
@Composable
fun AppBottomNav(
    selected: AppTab,
    avatarUrl: String?,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val tabs = AppTab.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(NavHeight),
        ) {
            val tabWidth = maxWidth / tabs.size

            // Which way did the selection move? The leading edge of the pill then springs faster
            // than the trailing one, which is what makes it stretch.
            val memory = remember { intArrayOf(selected.ordinal) }
            val direction = remember(selected) {
                val d = if (selected.ordinal >= memory[0]) 1 else -1
                memory[0] = selected.ordinal
                d
            }
            val fast = spring<Dp>(dampingRatio = 0.7f, stiffness = 700f)
            val slow = spring<Dp>(dampingRatio = 0.8f, stiffness = 260f)

            val targetLeft = tabWidth * selected.ordinal + (tabWidth - IndicatorWidth) / 2
            val left = animateDpAsState(
                targetValue = targetLeft,
                animationSpec = if (direction > 0) slow else fast,
                label = "navPillLeft",
            )
            val right = animateDpAsState(
                targetValue = targetLeft + IndicatorWidth,
                animationSpec = if (direction > 0) fast else slow,
                label = "navPillRight",
            )
            val pillColor = scheme.primaryContainer

            // The stretching pill sits behind the icons and is drawn directly from the animated
            // values, so moving it never recomposes the bar.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val l = left.value.toPx()
                        val width = (right.value - left.value).toPx().coerceAtLeast(24.dp.toPx())
                        val h = IndicatorHeight.toPx()
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(l, IndicatorTop.toPx()),
                            size = Size(width, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f),
                        )
                    },
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    NavTab(
                        tab = tab,
                        active = tab == selected,
                        avatarUrl = avatarUrl,
                        onClick = {
                            if (tab != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSelect(tab)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(
    tab: AppTab,
    active: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val tint by animateColorAsState(
        targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = GhMotion.defaultEffects(),
        label = "navTint",
    )
    val labelTint by animateColorAsState(
        targetValue = if (active) scheme.onBackground else scheme.onSurfaceVariant,
        animationSpec = GhMotion.defaultEffects(),
        label = "navLabelTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.86f
            active -> 1.1f
            else -> 1f
        },
        animationSpec = GhMotion.fastSpatial(),
        label = "navIconScale",
    )
    val ringColor by animateColorAsState(
        targetValue = if (active) scheme.onPrimaryContainer else Color.Transparent,
        animationSpec = GhMotion.defaultEffects(),
        label = "navAvatarRing",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = active,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .padding(top = IndicatorTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Same box the sliding pill occupies, so the icon is always centred on it.
        Box(
            modifier = Modifier.size(IndicatorWidth, IndicatorHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (tab == AppTab.Profile && !avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .border(1.5.dp, ringColor, CircleShape),
                )
            } else {
                val iconPath = when (tab) {
                    AppTab.Home -> if (active) NavIcons.Home else NavIcons.HomeOutline
                    AppTab.Discover -> NavIcons.Search
                    AppTab.Profile -> tab.icon
                }
                PathIcon(
                    pathData = iconPath,
                    tint = tint,
                    iconSize = 24.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = labelTint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
