package com.githunt.android.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.githunt.android.data.auth.User
import com.githunt.android.data.feed.PostItem
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.ui.auth.HomeState
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.Wordmark
import java.util.Locale

/**
 * The signed-in home: the GitHunt feed. If the email is not confirmed yet, a
 * confirm banner sits on top of it instead of blocking the whole app.
 * Grey top bar, grey bottom nav (Home + Sign out) around the feed.
 *
 * Repo cards here are the very same [RepoCard] Discover uses (shared README carousel, fonts,
 * in-card README), so the two screens can never drift apart again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    user: User,
    feed: FeedUiState,
    home: HomeState,
    onRefreshFeed: () -> Unit,
    onLoadMore: () -> Unit,
    onRefreshUser: (manual: Boolean) -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    scrollToTopTick: Int,
    bars: ScrollBarsState = rememberScrollBarsState(),
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var menuExpanded by remember { mutableStateOf(false) }
    val aiSearch = rememberAiSearchState()

    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    // Coming back from the mail app: check whether the link was opened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshUser(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background),
        ) {
            // Top bar: grey, and it runs up behind the status bar. The status bar strip always
            // stays; the bar itself slides away while scrolling down the feed (ScrollBars.kt).
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceVariant)
                    .statusBarsPadding(),
            )
            AnimatedVisibility(
                visible = !bars.hidden,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = MenuBarPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Wordmark(size = 26.sp)
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(MenuButtonSize),
                ) {
                    Canvas(Modifier.size(width = 22.dp, height = 18.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(
                            color = scheme.onSurface,
                            start = Offset(size.width * 0.08f, size.height * 0.30f),
                            end = Offset(size.width * 0.92f, size.height * 0.30f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = scheme.onSurface,
                            start = Offset(size.width * 0.08f, size.height * 0.72f),
                            end = Offset(size.width * 0.68f, size.height * 0.72f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            }

            PullToRefreshBox(
                isRefreshing = feed.refreshing,
                onRefresh = onRefreshFeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(bars.connection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!user.emailVerified) {
                        item(key = "confirm-email") {
                            ConfirmEmailBanner(home = home, onRefreshUser = onRefreshUser, onResend = onResend)
                        }
                    }

                    // AI search: the box, then any repos its searches found, then the normal feed.
                    item(key = "ai-search") { AiSearchPanel(state = aiSearch) }
                    aiSearchResults(aiSearch)

                    if (feed.loading && feed.items.isEmpty()) {
                        // Same placeholder cards Discover shows while it loads.
                        items(3, key = { index -> "skeleton-$index" }) {
                            RepoCardSkeleton()
                        }
                    } else if (feed.items.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = feed.error ?: "Nothing here yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlineButton(text = "Try again", onClick = onRefreshFeed)
                            }
                        }
                    }

                    items(feed.items, key = { it.key }) { item ->
                        when (item) {
                            is RepoItem -> RepoCard(item = item)
                            is PostItem -> PostCard(item)
                        }
                    }

                    item(key = "footer") {
                        if (feed.hasMore) {
                            // Reaching the footer means the person scrolled to the end: fetch older items.
                            LaunchedEffect(feed.items.size) { onLoadMore() }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (feed.loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (feed.error != null && feed.items.isNotEmpty()) {
                            Text(
                                text = "Couldn't load more. Tap to retry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onLoadMore() }
                                    .padding(16.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // The menu grows out of the menu icon and fully covers it while open.
        ExpandingMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp - MenuCardInset, end = MenuBarPadding - MenuCardInset),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleTheme(!darkTheme); menuExpanded = false }
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (darkTheme) "Dark" else "Light",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = { onToggleTheme(it); menuExpanded = false },
                    modifier = Modifier.scale(0.8f),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outline.copy(alpha = 0.35f)))
            QuickMenuAction("Notifications", onClick = { menuExpanded = false; onOpenNotifications() })
            QuickMenuAction("Clipped repos", onClick = { menuExpanded = false; onOpenClips() })
            QuickMenuAction("Settings", onClick = { menuExpanded = false; onOpenSettings() })
        }
    }
}

// Menu geometry. The top bar pads its content by MenuBarPadding at the sides and 12.dp on top, and
// the icon button is MenuButtonSize square, so the card (inset by MenuCardInset past the button's
// top/right edges) sits right on top of it and hides it completely.
private val MenuBarPadding = 20.dp
private val MenuButtonSize = 40.dp
private val MenuCardInset = 4.dp
private val MenuWidth = 220.dp

// Where the icon's centre sits inside the card, measured from the card's top-right corner.
private val MenuPivot = MenuCardInset + MenuButtonSize / 2

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * A small floating card that scales up out of the menu icon (and back into it on close).
 * Replaces Material's DropdownMenu, whose own popup container was drawing a square backing
 * rectangle behind the rounded corners.
 *
 * Place it inside a Box, aligned top-end, over the icon.
 */
@Composable
private fun ExpandingMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) {
            tween(durationMillis = 340, easing = EmphasizedDecelerate)
        } else {
            tween(durationMillis = 200, easing = EmphasizedAccelerate)
        },
        label = "menu-expand",
    )

    if (!expanded && progress <= 0f) return

    BackHandler(enabled = expanded, onBack = onDismiss)

    if (expanded) {
        // Invisible full-screen layer: tap anywhere outside the card to close it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    Surface(
        modifier = modifier
            .width(MenuWidth)
            .graphicsLayer {
                scaleX = lerp(0.18f, 1f, progress)
                scaleY = lerp(0.14f, 1f, progress)
                alpha = (progress * 4f).coerceIn(0f, 1f)
                if (size.width > 0f && size.height > 0f) {
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (size.width - MenuPivot.toPx()) / size.width,
                        pivotFractionY = MenuPivot.toPx() / size.height,
                    )
                }
            },
        color = scheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun ConfirmEmailBanner(
    home: HomeState,
    onRefreshUser: (manual: Boolean) -> Unit,
    onResend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(16.dp),
    ) {
        Text(
            text = "Confirm your email",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Open the link we sent you, then tap the button.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "I've confirmed it",
            onClick = { onRefreshUser(true) },
            enabled = !home.resending,
            loading = home.refreshing,
        )
        Spacer(Modifier.height(8.dp))
        OutlineButton(
            text = "Resend email",
            onClick = onResend,
            enabled = !home.refreshing,
            loading = home.resending,
        )
        home.notice?.let { notice ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PostCard(item: PostItem) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = item.avatarUrl)
            Spacer(Modifier.size(10.dp))
            Text(
                text = item.author,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.body.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onBackground,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "likes ${compact(item.likes)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = "comments ${compact(item.comments)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Avatar(url: String?, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun QuickMenuAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
