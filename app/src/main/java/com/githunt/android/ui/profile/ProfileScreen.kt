package com.githunt.android.ui.profile

import android.content.Intent
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.githunt.android.BuildConfig
import com.githunt.android.data.auth.User
import com.githunt.android.data.profile.FollowingPost
import com.githunt.android.data.profile.FollowingRepo
import com.githunt.android.data.profile.ProfilePost
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.RepoAvatar
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.SkeletonBar
import com.githunt.android.ui.components.formatCount
import com.githunt.android.ui.components.relativeTime

private val AccentLight = Color(0xFF0866FF)
private val AccentDark = Color(0xFF6EA8FE)

/**
 * The website's own profile page: cover photo, a rounded sheet with the avatar straddling the
 * seam, counts, Edit / Share, About, and the Posts / Following / Repos / Clipped tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    viewModel: ProfileViewModel,
    onRefreshUser: (manual: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    scrollToTopTick: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val base = remember { BuildConfig.API_BASE_URL.trimEnd('/') }
    val accent = if (isSystemInDarkTheme()) AccentDark else AccentLight
    var handledTick by rememberSaveable { mutableStateOf(scrollToTopTick) }

    LaunchedEffect(user.id, user.githubUsername) { viewModel.bind(user) }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick != handledTick) {
            handledTick = scrollToTopTick
            listState.animateScrollToItem(0)
        }
    }

    fun openWeb(path: String) {
        runCatching { uriHandler.openUri("$base/$path") }
    }

    fun shareProfile() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$base/u/${user.id}")
        }
        context.startActivity(Intent.createChooser(send, "Share profile"))
    }

    val tabs = if (user.githubUsername != null) {
        listOf(ProfileTab.Posts, ProfileTab.Following, ProfileTab.Repos, ProfileTab.Clipped)
    } else {
        listOf(ProfileTab.Posts, ProfileTab.Following, ProfileTab.Clipped)
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            viewModel.refresh()
            onRefreshUser(true)
        },
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                ProfileHeader(
                    user = user,
                    postCount = if (state.posts.loaded) state.posts.items.size else null,
                    tabs = tabs,
                    selected = state.tab,
                    accent = accent,
                    onSelectTab = viewModel::selectTab,
                    onEdit = onEditProfile,
                    onShare = { shareProfile() },
                    onGithub = {
                        user.githubUsername?.let { runCatching { uriHandler.openUri("https://github.com/$it") } }
                    },
                    onSettings = {
                        onOpenSettings()
                    },
                )
            }

            when (state.tab) {
                ProfileTab.Posts -> {
                    val section = state.posts
                    if (!section.loaded || (section.loading && section.items.isEmpty())) {
                        skeletons()
                    } else if (section.items.isEmpty()) {
                        note(section.error ?: "You haven't posted anything yet.")
                    } else {
                        items(section.items, key = { "post-${it.id}" }) { post ->
                            Padded { ProfilePostCard(post) }
                        }
                    }
                }
                ProfileTab.Following -> {
                    val section = state.following
                    if (!section.loaded || (section.loading && section.items.isEmpty())) {
                        skeletons()
                    } else if (section.items.isEmpty()) {
                        note(section.error ?: "Not following anyone yet \u2014 follow people and repo owners to see their activity here.")
                    } else {
                        items(section.items, key = { it.key }) { entry ->
                            Padded {
                                when (entry) {
                                    is FollowingPost -> ProfilePostCard(entry.post)
                                    is FollowingRepo -> RepoCard(item = entry.repo)
                                }
                            }
                        }
                    }
                }
                ProfileTab.Repos -> {
                    val section = state.repos
                    if (!section.loaded || (section.loading && section.items.isEmpty())) {
                        skeletons()
                    } else if (section.items.isEmpty()) {
                        note(section.error ?: "No repos found on @${user.githubUsername}.")
                    } else {
                        items(section.items, key = { "repo-${it.fullName}" }) { repoItem ->
                            Padded { RepoCard(item = repoItem) }
                        }
                    }
                }
                ProfileTab.Clipped -> {
                    val section = state.clipped
                    if (!section.loaded || (section.loading && section.items.isEmpty())) {
                        skeletons()
                    } else if (section.items.isEmpty()) {
                        note(section.error ?: "Nothing clipped yet \u2014 save a repo from Discover to see it here. Only you can see this.")
                    } else {
                        items(section.items, key = { "clip-${it.fullName}" }) { repoItem ->
                            Padded {
                                RepoCard(
                                    item = repoItem,
                                    clipped = true,
                                    onClipToggle = { viewModel.unclip(repoItem) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun LazyListScope.skeletons() {
    items(2, key = { index -> "skeleton-$index" }) {
        Padded { RepoCardSkeleton() }
    }
}

private fun LazyListScope.note(text: String) {
    item(key = "note") {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun ProfileHeader(
    user: User,
    postCount: Int?,
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    accent: Color,
    onSelectTab: (ProfileTab) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onGithub: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val coverHeight = 210.dp + statusTop
    val sheetTop = coverHeight - 24.dp
    val avatarTop = sheetTop - 84.dp
    val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
    // The web editor stores uploaded covers as data:image/...;base64,... in
    // background_url. Browsers render that directly, but Coil's normal
    // network model does not decode a data URI. Decode it to bytes off the
    // main thread; keep ordinary HTTPS URLs unchanged.
    val coverImageModel by produceState<Any?>(
        initialValue = null,
        key1 = user.backgroundUrl,
    ) {
        value = withContext(Dispatchers.IO) {
            profileCoverImageModel(user.backgroundUrl)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Cover: the person's photo, or a soft grey gradient. Dark fade at the top keeps the status bar readable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .background(Brush.linearGradient(listOf(scheme.outline, scheme.surface))),
        ) {
            if (coverImageModel != null) {
                AsyncImage(
                    model = coverImageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop + 72.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x73000000), Color.Transparent))),
            )
        }

        // Sheet: rounded top corners overlap the cover.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sheetTop)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(scheme.background),
        ) {
            Spacer(Modifier.height(72.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${user.username}",
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatText(
                        count = user.followerCount.toString(),
                        label = if (user.followerCount == 1) "follower" else "followers",
                    )
                    Dot()
                    StatText(count = user.followingCount.toString(), label = "following")
                    Dot()
                    StatText(
                        count = postCount?.toString() ?: "\u2026",
                        label = if (postCount == 1) "post" else "posts",
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    text = "Edit profile",
                    icon = NavIcons.Edit,
                    modifier = Modifier.weight(1f),
                    container = AccentLight,
                    content = Color.White,
                    bordered = false,
                    onClick = onEdit,
                )
                ActionButton(
                    text = "Share profile",
                    icon = NavIcons.Share,
                    modifier = Modifier.weight(1f),
                    container = scheme.surface,
                    content = scheme.onBackground,
                    bordered = true,
                    onClick = onShare,
                )
            }

            // About
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "About",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        PathIcon(pathData = NavIcons.Edit, tint = scheme.onSurfaceVariant, iconSize = 20.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val bio = user.bio?.takeIf { it.isNotBlank() }
                if (bio != null) {
                    Text(
                        text = bio,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = scheme.onBackground,
                    )
                } else {
                    Text(
                        text = "Add a bio so people know who you are.",
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (user.githubUsername != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onGithub)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PathIcon(pathData = NavIcons.Code, tint = scheme.onBackground, iconSize = 24.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "@${user.githubUsername}",
                            fontSize = 16.sp,
                            color = scheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        PathIcon(pathData = NavIcons.ChevronRight, tint = scheme.onSurfaceVariant, iconSize = 20.dp)
                    }
                }
            }

            // Content filter chips: the active one gets a soft tinted pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (active) accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (active) accent else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Avatar, straddling the seam between cover and sheet.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = avatarTop)
                .size(136.dp)
                .clip(CircleShape)
                .background(scheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PathIcon(pathData = NavIcons.Person, tint = scheme.onSurfaceVariant, iconSize = 56.dp)
                }
            }
        }

        // Settings button floating on the cover.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                PathIcon(pathData = NavIcons.Settings, tint = Color.White, iconSize = 20.dp)
            }
        }
    }
}

@Composable
private fun StatText(count: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onBackground)) {
                append(count)
            }
            append(" ")
            append(label)
        },
        fontSize = 14.sp,
        color = scheme.onSurfaceVariant,
    )
}

@Composable
private fun Dot() {
    Text(
        text = "\u00B7",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: String,
    modifier: Modifier,
    container: Color,
    content: Color,
    bordered: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(container)
            .then(if (bordered) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathIcon(pathData = icon, tint = content, iconSize = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfilePostCard(post: ProfilePost) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape),
    ) {
        if (post.imageUrl != null) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepoAvatar(url = post.avatarUrl, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val ago = relativeTime(post.createdAt)
                    if (ago.isNotEmpty()) {
                        Text(
                            text = ago,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onBackground,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "likes ${formatCount(post.likes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "comments ${formatCount(post.comments)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}


/**
 * Matches the web profile's cover-photo storage format.
 * /profile/edit uses FileReader.readAsDataURL(), so background_url can be a
 * base64 data URI rather than a normal HTTP URL.
 */
private fun profileCoverImageModel(backgroundUrl: String?): Any? {
    val value = backgroundUrl?.takeIf { it.isNotBlank() } ?: return null
    if (!value.startsWith("data:image/", ignoreCase = true)) return value

    val comma = value.indexOf(',')
    if (comma < 0 || !value.substring(0, comma).contains(";base64", ignoreCase = true)) {
        return null
    }

    return runCatching {
        Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
    }.getOrNull()
}
