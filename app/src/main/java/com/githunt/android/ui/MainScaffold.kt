package com.githunt.android.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.githunt.android.ui.components.GhContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.data.auth.User
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.settings.ThemeMode
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.auth.HomeState
import com.githunt.android.ui.clips.ClippedReposScreen
import com.githunt.android.ui.components.AppBottomNav
import com.githunt.android.ui.components.AppTab
import com.githunt.android.ui.components.OutlineButton
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.SubPageBar
import com.githunt.android.ui.components.relativeTime
import com.githunt.android.ui.components.rememberScrollBarsState
import com.githunt.android.ui.discover.DiscoverScreen
import com.githunt.android.ui.discover.DiscoverViewModel
import com.githunt.android.ui.feed.FeedScreen
import com.githunt.android.ui.feed.FeedUiState
import com.githunt.android.ui.profile.ProfileScreen
import com.githunt.android.ui.profile.ProfileViewModel
import com.githunt.android.ui.settings.SettingsHost
import com.githunt.android.ui.settings.SettingsViewModel
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.launch

/**
 * The signed-in app: Home (feed), Discover and Profile behind one bottom dock, like the website.
 * Each tab keeps its scroll position when you switch away and back; tapping the tab you are
 * already on scrolls it to the top.
 *
 * Pages opened from the home menu (Notifications, Clipped repos) and Settings are full-screen:
 * they hide the bottom dock and have a back button instead.
 */
@Composable
fun MainScaffold(
    user: User,
    feed: FeedUiState,
    home: HomeState,
    onRefreshFeed: () -> Unit,
    onLoadMore: () -> Unit,
    onRefreshUser: (manual: Boolean) -> Unit,
    onUserUpdated: (User) -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
    discoverViewModel: DiscoverViewModel,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel,
    clipsRepository: ClipsRepository,
    /** Goes up by one each time a system notification is tapped; opens the Notifications page. */
    openNotificationsTick: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var homeTap by remember { mutableIntStateOf(0) }
    var discoverTap by remember { mutableIntStateOf(0) }
    var profileTap by remember { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Which Settings page to land on (null = the main list). "Edit profile" buttons use "edit-profile".
    var settingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var quickPage by rememberSaveable { mutableStateOf<String?>(null) }
    val themeMode by settingsViewModel.prefs.theme.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val stateHolder = rememberSaveableStateHolder()

    // Home's feed hides the top bar and this bottom dock while you scroll down it.
    val homeBars = rememberScrollBarsState()
    LaunchedEffect(tab) { homeBars.show() }

    // Tapping a system notification asks for the Notifications page.
    LaunchedEffect(openNotificationsTick) {
        if (openNotificationsTick > 0) {
            settingsOpen = false
            settingsRoute = null
            quickPage = "notifications"
        }
    }

    BackHandler(enabled = settingsOpen) {
        settingsOpen = false
        settingsRoute = null
    }
    BackHandler(enabled = !settingsOpen && quickPage != null) { quickPage = null }
    BackHandler(enabled = !settingsOpen && quickPage == null && tab != AppTab.Home) { tab = AppTab.Home }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Settings, Clipped repos and Notifications sit one level above the tabs, like pages
            // pushed onto a navigation stack: opening one slides it in, going back slides it out.
            val page = if (settingsOpen) "settings" else quickPage ?: "tabs"
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val from = if (initialState == "tabs") 0 else 1
                    val to = if (targetState == "tabs") 0 else 1
                    pageTransition(forward = to > from, targetDepth = to)
                },
                label = "main-pages",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                when (shown) {
                    "settings" -> SettingsHost(
                        user = user,
                        viewModel = settingsViewModel,
                        onClose = {
                            settingsOpen = false
                            settingsRoute = null
                        },
                        onSignOut = onSignOut,
                        onUserUpdated = onUserUpdated,
                        onRefreshUser = { onRefreshUser(true) },
                        initialRoute = settingsRoute,
                    )
                    "clips" -> ClippedReposScreen(repository = clipsRepository, onBack = { quickPage = null })
                    "notifications" -> NotificationInboxScreen(viewModel = settingsViewModel, onBack = { quickPage = null })
                    // The tabs page owns the bottom dock, so the dock slides and fades with the
                    // page. Before, it popped in at the start of the back animation, which resized
                    // the page mid-transition and made leaving Settings look like a sudden jump.
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scheme.background),
                    ) {
                        // Each tab keeps its own saved state (scroll position and so on) while it
                        // slides in or out, and the dock below stays put.
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { tabTransition(forward = targetState.ordinal > initialState.ordinal) },
                            label = "tabs",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) { shownTab ->
                            stateHolder.SaveableStateProvider(shownTab.name) {
                                when (shownTab) {
                                    AppTab.Home -> FeedScreen(
                                        user = user,
                                        feed = feed,
                                        home = home,
                                        onRefreshFeed = onRefreshFeed,
                                        onLoadMore = onLoadMore,
                                        onRefreshUser = onRefreshUser,
                                        onResend = onResend,
                                        onSignOut = onSignOut,
                                        onOpenClips = { quickPage = "clips" },
                                        onOpenNotifications = { quickPage = "notifications" },
                                        onOpenSettings = { settingsOpen = true },
                                        darkTheme = darkTheme,
                                        onToggleTheme = { settingsViewModel.prefs.setTheme(if (it) ThemeMode.Dark else ThemeMode.Light) },
                                        scrollToTopTick = homeTap,
                                        bars = homeBars,
                                    )
                                    AppTab.Discover -> DiscoverScreen(
                                        viewModel = discoverViewModel,
                                        userId = user.id,
                                        scrollToTopTick = discoverTap,
                                    )
                                    AppTab.Profile -> ProfileScreen(
                                        user = user,
                                        viewModel = profileViewModel,
                                        onRefreshUser = onRefreshUser,
                                        onOpenSettings = { settingsOpen = true },
                                        onEditProfile = {
                                            settingsRoute = "edit-profile"
                                            settingsOpen = true
                                        },
                                        scrollToTopTick = profileTap,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = homeBars.collapseModifier(
                                slideUp = false,
                                active = { tab == AppTab.Home },
                            ),
                        ) {
                        AppBottomNav(
                            selected = tab,
                            avatarUrl = user.avatarUrl,
                            onSelect = { picked ->
                                if (picked == tab) {
                                    when (picked) {
                                        AppTab.Home -> homeTap++
                                        AppTab.Discover -> discoverTap++
                                        AppTab.Profile -> profileTap++
                                    }
                                } else {
                                    tab = picked
                                }
                            },
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mono = LocalGhMono.current
    val state by viewModel.inbox.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshNotificationInbox() }
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        SubPageBar(title = "Notifications", onBack = onBack, subtitle = "Last 24 hours")
        Box(modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        strokeWidth = 2.dp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                state.error != null -> PageMessage(
                    title = "Couldn't load notifications",
                    body = state.error.orEmpty(),
                    actionLabel = "Try again",
                    onAction = { viewModel.loadNotificationInbox() },
                )
                state.items.isEmpty() -> PageMessage(
                    title = "You're all caught up",
                    body = "No notifications in the last 24 hours. New alerts will appear here.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(scheme.surface)
                                .border(1.dp, scheme.outline.copy(alpha = 0.55f), shape)
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val ago = relativeTime(item.createdAt)
                                if (ago.isNotEmpty()) {
                                    Text(
                                        text = ago,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                                        fontFamily = mono,
                                        fontSize = 12.sp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (item.body.isNotBlank()) {
                                Text(
                                    text = item.body,
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "footer") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

/** Centered title + message (and an optional button) for empty and error states on these pages. */
@Composable
internal fun PageMessage(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            OutlineButton(text = actionLabel, onClick = onAction)
        }
    }
}
