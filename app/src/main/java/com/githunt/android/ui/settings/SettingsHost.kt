package com.githunt.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.auth.User
import com.githunt.android.data.settings.AppPreferences
import com.githunt.android.data.settings.ThemeMode
import com.githunt.android.data.settings.TranslationLanguages
import com.githunt.android.ui.components.AppIcons
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.pageTransition
import kotlinx.coroutines.launch

/**
 * The whole Settings area. It lives in one composable with its own tiny back stack (a comma
 * separated route list), so it needs no navigation library and survives rotation.
 */
@Composable
fun SettingsHost(
    user: User,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onSignOut: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onRefreshUser: () -> Unit,
    /** Open straight onto this page (for example "edit-profile"). Back from it closes Settings. */
    initialRoute: String? = null,
) {
    var stackKey by rememberSaveable { mutableStateOf(initialRoute ?: "root") }

    fun pop() {
        if (stackKey.contains(',')) stackKey = stackKey.substringBeforeLast(',') else onClose()
    }

    fun push(route: String) {
        stackKey = "$stackKey,$route"
    }

    BackHandler { pop() }

    AnimatedContent(
        targetState = stackKey,
        transitionSpec = {
            val from = initialState.count { it == ',' }
            val to = targetState.count { it == ',' }
            pageTransition(forward = to > from, targetDepth = to)
        },
        label = "settings-navigation",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { current ->
        when (current.substringAfterLast(',')) {
            "account" -> AccountScreen(onBack = { pop() }, onNavigate = { push(it) })
            "edit-profile" -> EditProfileScreen(
                user = user,
                vm = viewModel,
                onBack = { pop() },
                onUserUpdated = onUserUpdated,
                onRefreshUser = onRefreshUser,
            )
            "password" -> PasswordScreen(user = user, vm = viewModel, onBack = { pop() })
            "security" -> SecurityScreen(user = user, vm = viewModel, onBack = { pop() }, onSignOut = onSignOut)
            "manage" -> ManageAccountScreen(user = user, vm = viewModel, onBack = { pop() }, onSignOut = onSignOut)
            "language" -> LanguageScreen(prefs = viewModel.prefs, onBack = { pop() })
            "personalization" -> PersonalizationScreen(vm = viewModel, onBack = { pop() })
            "notifications" -> NotificationsScreen(vm = viewModel, onBack = { pop() })
            "api" -> ApiConnectionScreen(onBack = { pop() }, onNavigate = { push(it) })
            "api-github" -> GithubApiScreen(vm = viewModel, onBack = { pop() })
            "about" -> AboutScreen(onBack = { pop() }, onNavigate = { push(it) })
            "what" -> WhatIsThisScreen(onBack = { pop() })
            "howto" -> HowToUseScreen(onBack = { pop() })
            "info" -> InfoScreen(vm = viewModel, onBack = { pop() })
            "logs" -> LogsScreen(vm = viewModel, onBack = { pop() })
            else -> SettingsHome(
                user = user,
                prefs = viewModel.prefs,
                onBack = { pop() },
                onNavigate = { push(it) },
                onSignOut = onSignOut,
            )
        }
    }
}

private class Entry(val id: String, val search: String, val content: @Composable () -> Unit)

private class Group(
    val id: String,
    val title: String,
    val description: String,
    val entries: List<Entry>,
)

@Composable
private fun SettingsHome(
    user: User,
    prefs: AppPreferences,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val language by prefs.language.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }

    val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
    val languageLabel = TranslationLanguages.label(language)

    // Every row on the page, grouped. `search` is the lowercase text the search field matches.
    val groups = listOf(
        Group(
            id = "account",
            title = "Your account",
            description = "Manage your profile, password and security.",
            entries = listOf(
                Entry("profile", "$name account profile password security email username") {
                    SettingsRow(
                        title = name,
                        subtitle = "Profile, password & security",
                        leading = { AvatarCircle(url = user.avatarUrl) },
                        onClick = { onNavigate("account") },
                        trailing = { ChevronTrailing() },
                    )
                },
                Entry("manage", "manage account delete account remove") {
                    SettingsRow(
                        title = "Manage Account",
                        subtitle = "Delete account",
                        icon = SettingsIcons.Delete,
                        danger = true,
                        onClick = { onNavigate("manage") },
                        trailing = { ChevronTrailing() },
                    )
                },
            ),
        ),
        Group(
            id = "preferences",
            title = "Preferences",
            description = "Customize your experience on GitHunt.",
            entries = listOf(
                Entry("appearance", "appearance theme dark mode light mode display amoled black pure black") {
                    SettingsPickerRow(
                        title = "Appearance",
                        subtitle = theme.label,
                        icon = SettingsIcons.Brightness,
                    ) {
                        GhSegmentedPicker(
                            items = ThemeMode.values().toList(),
                            selected = theme,
                            onSelect = prefs::setTheme,
                            label = { it.short },
                        )
                    }
                },
                Entry("language", "translation language translate $languageLabel") {
                    SettingsRow(
                        title = "Translation Language",
                        subtitle = languageLabel,
                        icon = SettingsIcons.Translate,
                        onClick = { onNavigate("language") },
                        trailing = { ChevronTrailing() },
                    )
                },
                Entry("personalization", "personalization personalisation learns recommendations feed interests") {
                    SettingsRow(
                        title = "Personalization",
                        subtitle = "What GitHunt learns from how you use it",
                        icon = SettingsIcons.Tune,
                        onClick = { onNavigate("personalization") },
                        trailing = { ChevronTrailing() },
                    )
                },
                Entry("notifications", "notifications push alerts trending repos daily digest") {
                    SettingsRow(
                        title = "Notifications",
                        subtitle = "Alerts when a repo starts trending",
                        icon = SettingsIcons.Notifications,
                        onClick = { onNavigate("notifications") },
                        trailing = { ChevronTrailing() },
                    )
                },
            ),
        ),
        Group(
            id = "tools",
            title = "Tools and resources",
            description = "Connect GitHunt to the services you use.",
            entries = listOf(
                Entry("api", "api connection github token pat rate limit") {
                    SettingsRow(
                        title = "API Connection",
                        subtitle = "Use your own GitHub API token",
                        icon = NavIcons.Code,
                        onClick = { onNavigate("api") },
                        trailing = { ChevronTrailing() },
                    )
                },
            ),
        ),
        Group(
            id = "about",
            title = "About and legal",
            description = "Learn how GitHunt works and review our policies.",
            entries = listOf(
                Entry("about", "about what is this how to use legal terms privacy logs changelog version") {
                    SettingsRow(
                        title = "About",
                        subtitle = "What this is, how to use it, legal",
                        icon = SettingsIcons.Info,
                        onClick = { onNavigate("about") },
                        trailing = { ChevronTrailing() },
                    )
                },
            ),
        ),
    )

    val q = query.trim().lowercase()
    val visible = groups
        .map { group ->
            // Matching a group's own name (for example "preferences") shows all of its rows.
            val rows = if (q.isNotEmpty() && !group.title.lowercase().contains(q)) {
                group.entries.filter { it.search.lowercase().contains(q) }
            } else {
                group.entries
            }
            group to rows
        }
        .filter { it.second.isNotEmpty() }

    SettingsScaffold(title = "Settings", onBack = onBack) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search settings") },
            leadingIcon = { PathIcon(pathData = NavIcons.Search, tint = scheme.onSurfaceVariant, iconSize = 22.dp) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        PathIcon(pathData = NavIcons.Close, tint = scheme.onSurfaceVariant, iconSize = 20.dp)
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                disabledContainerColor = scheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = scheme.primary,
            ),
        )

        visible.forEachIndexed { index, (group, rows) ->
            SettingsSection(title = group.title, description = group.description, first = index == 0) {
                SettingsGroup {
                    rows.forEach { entry -> key(entry.id) { entry.content() } }
                }
            }
        }

        if (visible.isEmpty()) {
            Text(
                text = "No settings match \u201C${query.trim()}\u201D.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
            )
        }

        // Logout stands alone at the very bottom, as on the website.
        if (q.isEmpty()) {
            SettingsActions(modifier = Modifier.padding(top = 24.dp)) {
                SettingsButton(
                    text = "Log out",
                    onClick = { confirmLogout = true },
                    kind = ButtonKind.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        PathIcon(pathData = AppIcons.SignOut, tint = LocalContentColor.current, iconSize = 20.dp)
                    },
                )
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out?",
            text = "You'll need to log in again to see your feed.",
            confirmLabel = "Log out",
            onConfirm = {
                confirmLogout = false
                onSignOut()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** "Choose theme" bottom sheet: System default, Light or Dark, as a segmented choice list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.background,
    ) {
        Text(
            text = "Choose theme",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        SettingsGroup {
            ThemeMode.values().forEach { mode ->
                SettingsRow(
                    title = mode.label,
                    selected = mode == current,
                    onClick = {
                        onPick(mode)
                        close()
                    },
                    trailing = {
                        if (mode == current) {
                            PathIcon(pathData = NavIcons.Check, tint = scheme.onPrimaryContainer, iconSize = 22.dp)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
