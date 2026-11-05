package com.githunt.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    // Coming back from the mail app: check whether the link was opened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshUser(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // Top bar: grey, and it runs up behind the status bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceVariant)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(size = 26.sp)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Canvas(Modifier.size(width = 28.dp, height = 24.dp)) {
                        val stroke = 2.5.dp.toPx()
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(272.dp).clip(RoundedCornerShape(24.dp)),
                ) {
                    Surface(
                        color = scheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .clickable { onToggleTheme(!darkTheme); menuExpanded = false }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                                    Text(if (darkTheme) "Dark" else "Light", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                }
                                Switch(checked = darkTheme, onCheckedChange = { onToggleTheme(it); menuExpanded = false })
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outline.copy(alpha = 0.35f)))
                            QuickMenuAction("Notifications", onClick = { menuExpanded = false; onOpenNotifications() })
                            QuickMenuAction("Clipped repos", onClick = { menuExpanded = false; onOpenClips() })
                            QuickMenuAction("Settings", onClick = { menuExpanded = false; onOpenSettings() })
                        }
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!user.emailVerified) {
                    item(key = "confirm-email") {
                        ConfirmEmailBanner(home = home, onRefreshUser = onRefreshUser, onResend = onResend)
                    }
                }

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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 15.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
