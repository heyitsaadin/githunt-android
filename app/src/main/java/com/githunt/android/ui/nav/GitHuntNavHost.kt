package com.githunt.android.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githunt.android.ui.auth.AuthViewModel
import com.githunt.android.ui.auth.LoginScreen
import com.githunt.android.ui.auth.SignupScreen
import com.githunt.android.ui.compose.ComposeScreen
import com.githunt.android.ui.feed.DiscoverScreen
import com.githunt.android.ui.profile.ProfileScreen
import com.githunt.android.ui.settings.SettingsScreen
import com.githunt.android.ui.auth.OAuthWebViewScreen

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val COMPOSE = "compose"
    const val SETTINGS = "settings"
    const val OAUTH_GOOGLE = "oauth_google"
    const val OAUTH_GITHUB_CONNECT = "oauth_github_connect"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.DISCOVER, "Discover", Icons.Default.Explore),
    BottomTab(Routes.COMPOSE, "Post", Icons.Default.AddBox),
    BottomTab(Routes.PROFILE, "Profile", Icons.Default.Person),
)

@Composable
fun GitHuntNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val sessionLoading by authViewModel.sessionLoading.collectAsState()

    if (sessionLoading) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier,
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentUser != null && currentRoute in bottomTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (currentUser != null) Routes.DISCOVER else Routes.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.DISCOVER) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onGoToSignup = { navController.navigate(Routes.SIGNUP) },
                    onGoogleSignIn = { navController.navigate(Routes.OAUTH_GOOGLE) },
                )
            }
            composable(Routes.SIGNUP) {
                SignupScreen(
                    onSignedUp = {
                        navController.navigate(Routes.DISCOVER) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onGoToLogin = { navController.popBackStack() },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen()
            }
            composable(Routes.COMPOSE) {
                ComposeScreen(
                    onDismiss = { navController.popBackStack() },
                    onPosted = {
                        navController.navigate(Routes.DISCOVER) {
                            popUpTo(Routes.DISCOVER) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onConnectGithub = { navController.navigate(Routes.OAUTH_GITHUB_CONNECT) },
                )
            }
            composable(Routes.OAUTH_GOOGLE) {
                OAuthWebViewScreen(
                    startPath = "api/auth/google",
                    onSuccess = {
                        authViewModel.refreshAfterOAuth()
                        navController.navigate(Routes.DISCOVER) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onCancelled = { navController.popBackStack() },
                    onError = { navController.popBackStack() },
                )
            }
            composable(Routes.OAUTH_GITHUB_CONNECT) {
                OAuthWebViewScreen(
                    startPath = "api/auth/github",
                    onSuccess = {
                        authViewModel.refreshAfterOAuth()
                        navController.popBackStack()
                    },
                    onCancelled = { navController.popBackStack() },
                    onError = { navController.popBackStack() },
                )
            }
        }
    }
}
