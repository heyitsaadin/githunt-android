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
import com.githunt.android.ui.UnavailableScreen
import com.githunt.android.ui.auth.AuthScreen
import com.githunt.android.ui.auth.AuthViewModel
import com.githunt.android.ui.auth.AuthViewModelFactory
import com.githunt.android.ui.auth.Session
import com.githunt.android.ui.home.HomeScreen
import com.githunt.android.ui.theme.GitHuntTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as GitHuntApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        is Session.SignedIn -> HomeScreen(
                            user = current.user,
                            state = home,
                            onRefresh = authViewModel::refreshUser,
                            onResend = authViewModel::resendVerification,
                            onSignOut = authViewModel::signOut,
                        )
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
