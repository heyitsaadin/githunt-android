package com.githunt.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.githunt.android.data.auth.User
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
) {
    val scheme = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var homeTap by remember { mutableIntStateOf(0) }
    var discoverTap by remember { mutableIntStateOf(0) }
    var profileTap by remember { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != AppTab.Home) { tab = AppTab.Home }

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
                        onSignOut = onSignOut,
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
