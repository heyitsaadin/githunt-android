package com.githunt.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.githunt.android.data.auth.User
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.settings.ThemeMode
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.auth.HomeState
import com.githunt.android.ui.components.AppBottomNav
import com.githunt.android.ui.components.AppTab
import com.githunt.android.ui.discover.DiscoverScreen
import com.githunt.android.ui.discover.DiscoverViewModel
import com.githunt.android.ui.feed.FeedScreen
import com.githunt.android.ui.feed.FeedUiState
import com.githunt.android.ui.profile.ProfileScreen
import com.githunt.android.ui.profile.ProfileViewModel
import com.githunt.android.ui.settings.SettingsHost
import com.githunt.android.ui.settings.SettingsViewModel

/**
 * The signed-in app: Home (feed), Discover and Profile behind one bottom dock, like the website.
 * Each tab keeps its scroll position when you switch away and back; tapping the tab you are
 * already on scrolls it to the top.
 */
@Composable
fun MainScaffold(
    user: User,
    feed: FeedUiState,
    home: HomeState,
    onRefreshFeed: () -> Unit,
    onLoadMore: () -> Unit,
    onRefreshUser: (manual: Boolean) -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
    discoverViewModel: DiscoverViewModel,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel,
    clipsRepository: ClipsRepository,
) {
    val scheme = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var homeTap by remember { mutableIntStateOf(0) }
    var discoverTap by remember { mutableIntStateOf(0) }
    var profileTap by remember { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var quickPage by rememberSaveable { mutableStateOf<String?>(null) }
    val themeMode by settingsViewModel.prefs.theme.collectAsState()
    val darkTheme = themeMode != ThemeMode.Light
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
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
            if (settingsOpen) {
                SettingsHost(
                    user = user,
                    viewModel = settingsViewModel,
                    onClose = { settingsOpen = false },
                    onSignOut = onSignOut,
                )
            } else if (quickPage == "clips") {
                ClipsScreen(repository = clipsRepository, onBack = { quickPage = null })
            } else if (quickPage == "notifications") {
                NotificationInboxScreen(viewModel = settingsViewModel, onBack = { quickPage = null })
            } else stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
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
                        scrollToTopTick = profileTap,
                    )
                }
            }
        }

        if (!settingsOpen) AppBottomNav(
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

@Composable
private fun ClipsScreen(repository: ClipsRepository, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<RepoItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) {
        when (val result = repository.all()) {
            is ApiResult.Ok -> items = result.value
            is ApiResult.Err -> error = result.message
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Clipped repos", style = MaterialTheme.typography.titleLarge)
        }
        when {
            items == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            error != null -> Text(error.orEmpty(), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
            items.isNullOrEmpty() -> Text("Nothing clipped yet. Save a repository from Discover to see it here.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                items(items.orEmpty(), key = { it.fullName }) { repo -> RepoCard(item = repo) }
            }
        }
    }
}

@Composable
private fun NotificationInboxScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.inbox.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadNotificationInbox() }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Notifications", style = MaterialTheme.typography.titleLarge)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Text(state.error.orEmpty(), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
            state.items.isEmpty() -> Text("No notifications in the last 24 hours. New alerts will appear here.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        if (item.body.isNotBlank()) Text(item.body, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.createdAt, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
