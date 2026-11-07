package com.githunt.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.githunt.android.ui.settings.SettingsViewModel
import com.githunt.android.ui.settings.SettingsViewModelFactory
import com.githunt.android.data.settings.ThemeMode
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

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory((application as GitHuntApp).container)
    }

    /** The "Confirm email" link in the verification email: https://<host>/api/auth/verify-email?token=... */
    private fun handleVerificationLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.path != "/api/auth/verify-email") return
        val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return
        authViewModel.confirmEmailFromLink(token)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVerificationLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Toasts such as "Email confirmed" from the view model.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.messages.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Only on a fresh launch: after a recreate Android hands us the same intent again.
        if (savedInstanceState == null) handleVerificationLink(intent)

        // Ask Android to run this window at the fastest refresh rate supported
        // by the device. The system can still apply user/battery policies.
        val maxRefreshRate = windowManager.defaultDisplay.supportedModes
            .maxOfOrNull { it.refreshRate }
        if (maxRefreshRate != null) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxRefreshRate
            }
        }

        // If the person turned system animations off, skip the typing splash.
        val animationsOff = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.prefs.theme.collectAsStateWithLifecycle()
            val useDarkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            GitHuntTheme(darkTheme = useDarkTheme) {
                val session by authViewModel.session.collectAsStateWithLifecycle()
                val home by authViewModel.home.collectAsStateWithLifecycle()

                // The typing splash plays once per launch. The app waits for it to finish, even if
                // the session check is already done.
                var splashPlayed by rememberSaveable { mutableStateOf(animationsOff) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!splashPlayed) {
                        LoadingScreen(animate = true, onAnimationFinished = { splashPlayed = true })
                    } else {
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
                                    onUserUpdated = authViewModel::updateUser,
                                    onResend = authViewModel::resendVerification,
                                    onSignOut = authViewModel::signOut,
                                    discoverViewModel = discoverViewModel,
                                    profileViewModel = profileViewModel,
                                    settingsViewModel = settingsViewModel,
                                    clipsRepository = (application as GitHuntApp).container.clipsRepository,
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
}
