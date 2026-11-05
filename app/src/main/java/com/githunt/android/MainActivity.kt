package com.githunt.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.ui.LoadingScreen
import com.githunt.android.ui.MainScaffold
import com.githunt.android.ui.UnavailableScreen
import com.githunt.android.ui.auth.AuthScreen
import com.githunt.android.ui.auth.AuthViewModel
import com.githunt.android.ui.auth.AuthViewModelFactory
import com.githunt.android.ui.auth.Session
import com.githunt.android.ui.discover.DiscoverViewModel
import com.githunt.android.ui.discover.DiscoverViewModelFactory
import com.githunt.android.ui.feed.FeedViewModel
import com.githunt.android.ui.feed.FeedViewModelFactory
import com.githunt.android.ui.profile.ProfileViewModel
import com.githunt.android.ui.profile.ProfileViewModelFactory
import com.githunt.android.ui.theme.GitHuntTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as GitHuntApp).container)
    }

    // Created lazily: only touched once someone is signed in.
    private val feedViewModel: FeedViewModel by viewModels {
        FeedViewModelFactory((application as GitHuntApp).container)
    }

    // Discover and Profile only start loading when their tab is first opened.
    private val discoverViewModel: DiscoverViewModel by viewModels {
        DiscoverViewModelFactory((application as GitHuntApp).container)
    }

    private val profileViewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory((application as GitHuntApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask Android to run this window at the fastest refresh rate supported
        // by the device. The system can still apply user/battery policies.
        val maxRefreshRate = windowManager.defaultDisplay.supportedModes
            .maxOfOrNull { it.refreshRate }
        if (maxRefreshRate != null) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxRefreshRate
            }
        }

        enableEdgeToEdge()
        setContent {
            GitHuntTheme {
                val session by authViewModel.session.collectAsStateWithLifecycle()
                val home by authViewModel.home.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (val current = session) {
                        Session.Loading -> LoadingScreen()
                        Session.SignedOut -> AuthScreen(viewModel = authViewModel)
                        is Session.SignedIn -> {
                            val feed by feedViewModel.state.collectAsStateWithLifecycle()
                            MainScaffold(
                                user = current.user,
                                feed = feed,
                                home = home,
                                onRefreshFeed = feedViewModel::refresh,
                                onLoadMore = feedViewModel::loadMore,
                                onRefreshUser = authViewModel::refreshUser,
                                onResend = authViewModel::resendVerification,
                                onSignOut = authViewModel::signOut,
                                discoverViewModel = discoverViewModel,
                                profileViewModel = profileViewModel,
                            )
                        }
                        is Session.Unavailable -> UnavailableScreen(
                            message = current.message,
                            onRetry = authViewModel::restore,
                        )
                    }
                }
            }
        }
    }
}
