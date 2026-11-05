package com.githunt.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** The three places the signed-in app lives, same as the website's dock: Feed, Discover, Profile. */
enum class AppTab(val label: String, val icon: String) {
    Home("Home", NavIcons.Home),
    Discover("Discover", NavIcons.Discover),
    Profile("Profile", NavIcons.Person),
}

private val DockHeight = 68.dp
private val DockCorner = 30.dp
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private val IndicatorTop = 9.dp

/**
 * Floating pill dock. One soft pill slides (with a little spring) under whichever tab is active,
 * the active icon pops up a touch, and a press squeezes the icon. Colours come only from the app's
 * monochrome scheme, so it follows light and dark mode with no accent colour.
 *
 * Layout note: it still sits below the content in a Column (nothing scrolls underneath it), so no
 * screen needs extra bottom padding.
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
    val shape = RoundedCornerShape(DockCorner)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.background)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockHeight)
                .shadow(elevation = 6.dp, shape = shape)
                .background(scheme.surface, shape)
                .border(1.dp, scheme.outline, shape),
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorX = animateDpAsState(
                targetValue = tabWidth * selected.ordinal + (tabWidth - IndicatorWidth) / 2,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "navIndicatorX",
            )

            // The sliding pill sits behind the icons. Its position is read in the layout phase,
            // so the slide never recomposes the dock.
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorX.value.roundToPx(), IndicatorTop.roundToPx()) }
                    .size(IndicatorWidth, IndicatorHeight)
                    .background(scheme.onSurface.copy(alpha = 0.12f), CircleShape),
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
        targetValue = if (active) scheme.onSurface else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "navTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.88f
            active -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navIconScale",
    )
    val ringColor by animateColorAsState(
        targetValue = if (active) scheme.onSurface else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(durationMillis = 180),
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
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .border(1.5.dp, ringColor, CircleShape),
                )
            } else {
                PathIcon(
                    pathData = tab.icon,
                    tint = tint,
                    iconSize = 24.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
