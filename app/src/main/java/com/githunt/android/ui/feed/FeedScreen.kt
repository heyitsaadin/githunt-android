package com.githunt.android.ui.feed

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.githunt.android.data.auth.User
import com.githunt.android.data.feed.FeedItem
import com.githunt.android.data.feed.PostItem
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.ui.auth.HomeState
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.PrimaryButton
import com.githunt.android.ui.components.StatusChip
import com.githunt.android.ui.components.Wordmark
import java.util.Locale

/**
 * The signed-in home: the GitHunt feed. If the email is not confirmed yet, a
 * confirm banner sits on top of it instead of blocking the whole app.
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
) {
    val scheme = MaterialTheme.colorScheme

    // Coming back from the mail app: check whether the link was opened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshUser(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark(size = 26.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (user.emailVerified) {
                    StatusChip(text = "VERIFIED", tone = scheme.primary)
                } else {
                    StatusChip(text = "UNVERIFIED", tone = scheme.error)
                }
                Text(
                    text = "SIGN OUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSignOut() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
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
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary)
                        }
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
                        is RepoItem -> RepoCard(item)
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
                                    color = scheme.primary,
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
                    Spacer(Modifier.navigationBarsPadding().height(8.dp))
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
private fun RepoCard(item: RepoItem) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .clickable(enabled = item.url != null) { item.url?.let { runCatching { uriHandler.openUri(it) } } }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = item.avatarUrl)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = item.owner,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.fullName.substringAfter('/'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item.description?.let { description ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.topics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.topics.take(3).forEach { topic ->
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .border(1.dp, scheme.outline, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item.language?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
            Text(
                text = "\u2605 ${compact(item.stars)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = "forks ${compact(item.forks)}",
                style = MaterialTheme.typography.labelSmall,
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
