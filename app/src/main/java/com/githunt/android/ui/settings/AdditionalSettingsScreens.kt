package com.githunt.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.githunt.android.data.settings.AppPreferences
import com.githunt.android.data.settings.TranslationLanguages

@Composable
fun LanguageScreen(prefs: AppPreferences, onBack: () -> Unit) {
    val selected by prefs.language.collectAsState()
    SettingsScaffold(title = "Translation language", onBack = onBack) {
        SettingsSection(
            title = "Choose a language",
            description = "Choose the language used for translated repository descriptions.",
            first = true,
        ) {
            TranslationLanguages.all.forEach { language ->
                SettingsRow(
                    title = language.label,
                    onClick = { prefs.setLanguage(language.code) },
                    trailing = { RadioButton(selected = selected == language.code, onClick = { prefs.setLanguage(language.code) }) },
                )
            }
        }
    }
}

@Composable
fun PersonalizationScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.personalization.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadPersonalization() }
    SettingsScaffold(title = "Personalization", onBack = onBack) {
        SettingsSection(
            title = "Your experience",
            description = "Control whether GitHunt uses your activity to personalize recommendations.",
            first = true,
        ) {
            SettingsRow(
                title = "Personalized recommendations",
                subtitle = if (state.enabled) "Personalization is on" else "Personalization is off",
                trailing = {
                    Switch(checked = state.enabled, onCheckedChange = vm::setPersonalization, enabled = !state.loading && !state.saving)
                },
            )
        }
        if (state.loading) SettingsNote("Loading your preferences…")
        state.error?.let { SettingsNote(it, error = true) }
        SettingsSection(title = "Your data", description = "Clear the personalization data associated with your account.") {
            SettingsActions {
                SettingsButton(
                    text = "Clear personalization data",
                    kind = ButtonKind.Outlined,
                    enabled = !state.saving,
                    loading = state.saving,
                    onClick = { confirmClear = true },
                )
            }
            if (state.cleared) SettingsNote("Your personalization data was cleared.")
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear personalization data?",
            text = "This clears the data used for personalized recommendations.",
            confirmLabel = "Clear data",
            destructive = true,
            onConfirm = { confirmClear = false; vm.clearPersonalization() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun NotificationsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.notifications.collectAsState()
    LaunchedEffect(Unit) { vm.loadNotifications() }
    SettingsScaffold(title = "Notifications", onBack = onBack) {
        SettingsSection(
            title = "Push notifications",
            description = "Choose which GitHunt updates you want to receive.",
            first = true,
        ) {
            SettingsRow(
                title = "Trending repository alerts",
                subtitle = "Get notified when repositories start trending.",
                trailing = { Switch(checked = state.hotAlerts, onCheckedChange = vm::setHotAlerts, enabled = !state.loading) },
            )
            SettingsRow(
                title = "Daily digest",
                subtitle = "A daily summary of activity and recommendations.",
                trailing = { Switch(checked = state.dailyDigest, onCheckedChange = vm::setDailyDigest, enabled = !state.loading) },
            )
        }
        if (state.loading) SettingsNote("Loading notification preferences…")
        state.error?.let { SettingsNote(it, error = true) }
        state.interestError?.let { SettingsNote(it, error = true) }
        SettingsSection(title = "Test notifications", description = "Send a test notification to check your device setup.") {
            SettingsActions {
                SettingsButton(
                    text = if (state.testSent) "Test sent" else "Send test notification",
                    onClick = vm::sendTestNotification,
                    loading = state.testing,
                    enabled = !state.loading,
                    kind = ButtonKind.Outlined,
                )
            }
        }
    }
}

@Composable
fun ApiConnectionScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScaffold(title = "API connection", onBack = onBack) {
        SettingsSection(
            title = "GitHub API",
            description = "Connect a GitHub personal access token to use your own API rate limits.",
            first = true,
        ) {
            SettingsRow(
                title = "GitHub token",
                subtitle = "View connection status, add or remove a token.",
                onClick = { onNavigate("api-github") },
                trailing = { ChevronTrailing() },
            )
        }
    }
}

@Composable
fun GithubApiScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.github.collectAsState()
    var token by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadGithub() }
    SettingsScaffold(title = "GitHub API token", onBack = onBack) {
        SettingsSection(
            title = "Connection",
            description = "Your token is stored by the GitHunt service and is never displayed after saving.",
            first = true,
        ) {
            when {
                state.loading -> SettingsNote("Checking connection…")
                state.status != null -> {
                    SettingsRow(
                        title = "Connected as ${state.status?.login ?: "GitHub user"}",
                        subtitle = "Your GitHub API token is connected.",
                    )
                    state.status?.rate?.let { rate ->
                        SettingsNote("Core API: ${rate.remaining} of ${rate.limit} requests remaining.")
                    }
                    SettingsActions {
                        SettingsButton(
                            text = "Remove token",
                            kind = ButtonKind.Danger,
                            loading = state.removing,
                            onClick = { confirmRemove = true },
                        )
                    }
                }
                else -> {
                    SettingsNote("No GitHub token is connected.")
                    SettingsField(
                        label = "Personal access token",
                        value = token,
                        onValueChange = { token = it },
                        password = true,
                        mono = true,
                        placeholder = "github_pat_…",
                    )
                    SettingsActions {
                        SettingsButton(
                            text = "Connect GitHub",
                            loading = state.phase == TokenPhase.Checking,
                            enabled = token.isNotBlank(),
                            onClick = { vm.connectGithub(token); token = "" },
                        )
                    }
                }
            }
            state.error?.let { SettingsNote(it, error = true) }
            if (state.phase == TokenPhase.Success) SettingsNote("GitHub token connected.")
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove GitHub token?",
            text = "GitHunt will stop using this token for GitHub API requests.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { confirmRemove = false; vm.removeGithubToken() },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScaffold(title = "About GitHunt", onBack = onBack) {
        SettingsSection(title = "Learn about GitHunt", description = "Information and help for using the app.", first = true) {
            SettingsRow(title = "What is GitHunt?", subtitle = "A quick introduction", onClick = { onNavigate("what") }, trailing = { ChevronTrailing() })
            SettingsRow(title = "How to use GitHunt", subtitle = "A short guide to the main features", onClick = { onNavigate("howto") }, trailing = { ChevronTrailing() })
        }
        SettingsSection(title = "App information", description = "Version information, build notes and feedback.") {
            SettingsRow(title = "Build information & feedback", onClick = { onNavigate("info") }, trailing = { ChevronTrailing() })
            SettingsRow(title = "Changelog", onClick = { onNavigate("logs") }, trailing = { ChevronTrailing() })
        }
    }
}

@Composable
fun WhatIsThisScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "What is GitHunt?", onBack = onBack) {
        SettingsSection(title = "Discover repositories", description = "GitHunt helps you explore open-source projects, discover developers and keep track of repositories that interest you.", first = true) {
            SettingsBody("Browse the feed, search for projects, and open repository pages to learn more about the code and the people building it.")
        }
    }
}

@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "How to use GitHunt", onBack = onBack) {
        SettingsSection(title = "Getting around", description = "Use the bottom navigation to move between the main areas of the app.", first = true) {
            SettingsRow(title = "Home", subtitle = "Browse repository recommendations.")
            SettingsRow(title = "Discover", subtitle = "Find repositories and developers.")
            SettingsRow(title = "Profile", subtitle = "View your profile and saved activity.")
        }
    }
}

@Composable
fun InfoScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    val state by vm.feature.collectAsState()
    SettingsScaffold(title = "App information & feedback", onBack = onBack) {
        SettingsSection(title = "GitHunt Android", description = "Send feedback or suggest an improvement.", first = true) {
            SettingsField(
                label = "Your feedback",
                value = message,
                onValueChange = { message = it },
                singleLine = false,
                minLines = 4,
                maxLength = 2000,
                placeholder = "What could be better?",
            )
            SettingsField(label = "Contact (optional)", value = contact, onValueChange = { contact = it }, placeholder = "Email or username")
            state.error?.let { SettingsNote(it, error = true) }
            if (state.sent) SettingsNote("Thanks. Your feedback was sent.")
            SettingsActions {
                SettingsButton(
                    text = "Send feedback",
                    loading = state.sending,
                    enabled = message.isNotBlank(),
                    onClick = { vm.sendFeatureRequest(message, contact); message = "" },
                )
            }
        }
    }
}

@Composable
fun LogsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.logs.collectAsState()
    LaunchedEffect(Unit) { vm.loadLogs() }
    SettingsScaffold(title = "Changelog", onBack = onBack) {
        SettingsSection(title = "Build history", description = "Recent app build notes and changes.", first = true) {
            if (state.logs == null && state.error == null) {
                SettingsNote("Loading build notes…")
            } else if (state.logs.isNullOrEmpty()) {
                SettingsNote(state.error ?: "No build notes are available.")
            } else {
                state.logs.orEmpty().forEach { log ->
                    SettingsRow(
                        title = log.title.ifBlank { log.version },
                        subtitle = listOfNotNull(log.version.takeIf { it.isNotBlank() }, log.date, log.buildNumber?.let { "Build $it" }).joinToString(" · "),
                    )
                    if (log.markdown.isNotBlank()) SettingsBody(log.markdown)
                }
            }
        }
    }
}
