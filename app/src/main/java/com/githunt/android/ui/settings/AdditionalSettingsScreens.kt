package com.githunt.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.githunt.android.BuildConfig
import com.githunt.android.data.settings.AppPreferences
import com.githunt.android.data.settings.TranslationLanguages
import com.githunt.android.data.settings.AppActivityLog
import com.githunt.android.data.settings.displayTime
import com.githunt.android.ui.components.GhSwitch
import com.githunt.android.ui.components.MarkdownBody
import com.githunt.android.ui.components.PathIcon

@Composable
fun LanguageScreen(prefs: AppPreferences, onBack: () -> Unit) {
    val selected by prefs.language.collectAsState()
    SettingsScaffold(title = "Translation language", onBack = onBack) {
        SettingsSection(
            title = "Choose a language",
            description = "Choose the language used for translated repository descriptions.",
            first = true,
        ) {
            SettingsGroup {
                TranslationLanguages.all.forEach { language ->
                    SettingsRow(
                        title = language.label,
                        selected = selected == language.code,
                        onClick = { prefs.setLanguage(language.code) },
                        trailing = { RadioButton(selected = selected == language.code, onClick = null) },
                    )
                }
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
                    GhSwitch(checked = state.enabled, onCheckedChange = vm::setPersonalization, enabled = !state.loading && !state.saving)
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
    val deviceOn by vm.deviceOn.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.enableDevice() else vm.notificationPermissionDenied()
    }
    LaunchedEffect(Unit) { vm.loadNotifications() }
    SettingsScaffold(title = "Notifications", onBack = onBack) {
        SettingsSection(
            title = "On this phone",
            description = "GitHunt checks for new alerts in the background about every 15 minutes and shows them here, even when the app is closed.",
            first = true,
        ) {
            SettingsRow(
                title = "Notifications on this phone",
                subtitle = when {
                    state.deviceBusy -> "Working…"
                    deviceOn -> "On"
                    else -> "Off"
                },
                trailing = {
                    GhSwitch(
                        checked = deviceOn,
                        enabled = !state.deviceBusy,
                        onCheckedChange = { on ->
                            when {
                                !on -> vm.disableDevice()
                                vm.needsNotificationPermission() ->
                                    permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                else -> vm.enableDevice()
                            }
                        },
                    )
                },
            )
            state.deviceError?.let { SettingsNote(it, error = true) }
            if (deviceOn && vm.notificationsBlockedBySystem()) {
                SettingsNote("Notifications are turned off for GitHunt in your phone's settings, so nothing will show.", error = true)
            }
            if (state.deviceBlocked || (deviceOn && vm.notificationsBlockedBySystem())) {
                SettingsActions {
                    SettingsButton(
                        text = "Open phone settings",
                        kind = ButtonKind.Outlined,
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
        SettingsSection(
            title = "Push notifications",
            description = "Choose which GitHunt updates you want to receive.",
        ) {
            SettingsGroup {
                SettingsRow(
                    title = "Trending repository alerts",
                    subtitle = "Get notified when repositories start trending.",
                    trailing = { GhSwitch(checked = state.hotAlerts, onCheckedChange = vm::setHotAlerts, enabled = !state.loading) },
                )
                SettingsRow(
                    title = "Daily digest",
                    subtitle = "A daily summary of activity and recommendations.",
                    trailing = { GhSwitch(checked = state.dailyDigest, onCheckedChange = vm::setDailyDigest, enabled = !state.loading) },
                )
            }
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
                    enabled = !state.loading && deviceOn,
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
    val openSite = rememberSiteOpener()
    SettingsScaffold(title = "About GitHunt", onBack = onBack) {
        SettingsSection(title = "About GitHunt", description = "Learn how GitHunt works and what's new.", first = true) {
            SettingsGroup {
                SettingsRow(title = "What is this", subtitle = "What GitHunt is", icon = SettingsIcons.Info, onClick = { onNavigate("what") }, trailing = { ChevronTrailing() })
                SettingsRow(title = "How to use", subtitle = "Posting, discovering, and more", icon = SettingsIcons.Book, onClick = { onNavigate("howto") }, trailing = { ChevronTrailing() })
                SettingsRow(title = "Info", subtitle = "Version, developer, and request a feature", icon = SettingsIcons.Info, onClick = { onNavigate("info") }, trailing = { ChevronTrailing() })
                SettingsRow(title = "Docs", subtitle = "Release notes and app documentation", icon = SettingsIcons.Book, onClick = { onNavigate("docs") }, trailing = { ChevronTrailing() })
                SettingsRow(title = "Logs", subtitle = "Requests and activity on this device", icon = SettingsIcons.Info, onClick = { onNavigate("logs") }, trailing = { ChevronTrailing() })
            }
        }
        SettingsSection(title = "Legal", description = "Review GitHunt's policies.") {
            SettingsGroup {
                SettingsRow(title = "Privacy Policy", icon = SettingsIcons.File, onClick = { openSite("privacy") }, trailing = { OpenTrailing() })
                SettingsRow(title = "Terms of Service", icon = SettingsIcons.File, onClick = { openSite("terms") }, trailing = { OpenTrailing() })
            }
        }
    }
}

@Composable
fun WhatIsThisScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "What is this", onBack = onBack) {
        SettingsSection(title = "GitHunt", description = "A feed for sharing images, files, links, and code, and for discovering GitHub projects other people are working on.", first = true) {}
        SettingsSection(title = "The feed") { SettingsBody("A single timeline that mixes posts from people you follow with trending repositories, so everyday sharing and the projects you care about live in one place.") }
        SettingsSection(title = "Discover") { SettingsBody("Browse GitHub repositories, including trending and top-rated projects, with likes, clips, and repository stats pulled from GitHub.") }
        SettingsSection(title = "Profiles") { SettingsBody("Every account has a profile with posts, an optional connected GitHub account, and a bio. Sign in with a username and password or with Google.") }
    }
}

@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "How to use", onBack = onBack) {
        SettingsSection(title = "Getting around", description = "Posting, discovering, and more.", first = true) {
            SettingsGroup {
                SettingsRow(title = "Post something", subtitle = "Use the create button to share text, an image, a file, a link, or a GitHub repository.")
                SettingsRow(title = "Search the feed", subtitle = "Use feed search to find posts and projects by keyword.")
                SettingsRow(title = "Discover repos", subtitle = "Open Discover to browse trending or top GitHub repositories with live stats.")
                SettingsRow(title = "Clip a repo", subtitle = "Save a repository privately to your Clipped list for later.")
                SettingsRow(title = "Like and comment", subtitle = "Like posts and open a post to join the conversation.")
                SettingsRow(title = "Connect GitHub", subtitle = "Connect a GitHub account from your profile editor so people can find your work.")
            }
        }
        SettingsSection(title = "Tutorial", description = "A quick reminder of the main app areas.") {
            SettingsGroup {
                SettingsRow(title = "Home", subtitle = "Your feed of posts and repository activity.")
                SettingsRow(title = "Discover", subtitle = "Explore repositories and developers.")
                SettingsRow(title = "Profile", subtitle = "Manage your profile, posts, follows, repositories, and clipped items.")
            }
        }
    }
}

private const val DeveloperGithubMark =
    "M12 .5C5.65 .5.5 5.65.5 12c0 5.1 3.3 9.4 7.9 10.9.6.1.8-.3.8-.6v-2.1c-3.2.7-3.9-1.4-3.9-1.4-.5-1.3-1.3-1.6-1.3-1.6-1-.7.1-.7.1-.7 1.1.1 1.7 1.2 1.7 1.2 1 .1.8 2.2 3.5 1.7.1-.7.4-1.2.7-1.5-2.6-.3-5.3-1.3-5.3-5.8 0-1.3.5-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.1 0 0 1-.3 3.2 1.2.9-.3 1.9-.4 2.9-.4s2 .1 2.9.4c2.2-1.5 3.2-1.2 3.2-1.2.6 1.6.2 2.8.1 3.1.8.8 1.2 1.8 1.2 3.1 0 4.5-2.7 5.5-5.3 5.8.4.3.8 1 .8 2v3c0 .3.2.7.8.6 4.6-1.5 7.9-5.8 7.9-10.9C23.5 5.65 18.35.5 12 .5z"

@Composable
private fun InfoIconSlot(pathData: String) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        PathIcon(
            pathData = pathData,
            tint = MaterialTheme.colorScheme.onBackground,
            iconSize = 22.dp,
        )
    }
}

@Composable
fun InfoScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    val state by vm.feature.collectAsState()
    val open = rememberWebOpener()
    SettingsScaffold(title = "Info", onBack = onBack) {
        SettingsSection(title = "GitHunt Android", description = "A feed for sharing images, files, and links, unified with GitHub project discovery.", first = true) {
            SettingsBody("Version ${BuildConfig.VERSION_NAME}")
        }
        SettingsSection(title = "How it works", description = "What you can do with GitHunt.") {
            SettingsGroup {
                SettingsRow(title = "Share", subtitle = "Post images, files, and links to a feed with activity from people you follow.", leading = { InfoIconSlot(SettingsIcons.Image) })
                SettingsRow(title = "Discover", subtitle = "Browse trending and top GitHub repositories with stars, forks, and live stats.", leading = { InfoIconSlot(SettingsIcons.Info) })
                SettingsRow(title = "Translate", subtitle = "Translate repository descriptions and READMEs in the app.", leading = { InfoIconSlot(SettingsIcons.Translate) })
                SettingsRow(title = "Your account, your data", subtitle = "Your profile, posts, and follows stay tied to your account.", leading = { InfoIconSlot(SettingsIcons.Shield) })
            }
        }
        SettingsSection(title = "Powered by the GitHub API", description = "Repository discovery, stats, and profile data are fetched live from GitHub. You can connect your own token in API Connection.") {}
        SettingsSection(title = "Developer", description = "Get in touch or follow along.") {
            SettingsGroup {
                SettingsRow(
                    title = "Aadin Kc",
                    subtitle = "Kerala, India",
                    leading = {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            AvatarCircle(
                                url = "https://avatars.githubusercontent.com/u/256625064?v=4",
                                size = 36.dp,
                            )
                        }
                    },
                )
                SettingsRow(title = "GitHub", subtitle = "@heyitsaadin", leading = { InfoIconSlot(DeveloperGithubMark) }, onClick = { open("https://github.com/heyitsaadin") }, trailing = { OpenTrailing() })
                SettingsRow(title = "LinkedIn", subtitle = "Aadin Kc", leading = { InfoIconSlot(SettingsIcons.LinkedIn) }, onClick = { open("https://www.linkedin.com/in/aadin-kc-128bb3371") }, trailing = { OpenTrailing() })
                SettingsRow(title = "X (Twitter)", subtitle = "@its_aadin", leading = { InfoIconSlot(SettingsIcons.XMark) }, onClick = { open("https://x.com/its_aadin") }, trailing = { OpenTrailing() })
                SettingsRow(title = "Email", subtitle = "hey.itsaadin@gmail.com", leading = { InfoIconSlot(SettingsIcons.Mail) }, onClick = { open("mailto:hey.itsaadin@gmail.com") }, trailing = { OpenTrailing() })
            }
        }
        SettingsSection(title = "Have an idea?", description = "Tell the developer what you'd like to see added or changed.") {
            SettingsField(label = "Your idea", value = message, onValueChange = { message = it }, singleLine = false, minLines = 4, maxLength = 2000, placeholder = "What would you like to see added or changed?")
            SettingsField(label = "Contact (optional)", value = contact, onValueChange = { contact = it }, placeholder = "Email or username", maxLength = 200)
            state.error?.let { SettingsNote(it, error = true) }
            if (state.sent) SettingsNote("Thanks. Your request was sent.")
            SettingsActions {
                SettingsButton(text = if (state.sent) "Sent" else "Send request", loading = state.sending, enabled = message.isNotBlank() && !state.sent, onClick = { vm.sendFeatureRequest(message, contact) })
            }
        }
    }
}

@Composable
fun LogsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.logs.collectAsState()
    LaunchedEffect(Unit) { vm.loadLogs() }
    SettingsScaffold(title = "Docs", onBack = onBack) {
        if (state.logs == null && state.error == null) {
            SettingsSection(title = "Build history", description = "Recent app build notes and changes.", first = true) {
                SettingsNote("Loading build notes…")
            }
        } else if (state.logs.isNullOrEmpty()) {
            SettingsSection(title = "Build history", description = "Recent app build notes and changes.", first = true) {
                SettingsNote(state.error ?: "No build notes are available.")
            }
        } else {
            state.logs.orEmpty().forEachIndexed { index, log ->
                SettingsSection(
                    title = log.title.ifBlank { log.version },
                    description = listOfNotNull(
                        log.version.takeIf { it.isNotBlank() },
                        log.date,
                        log.buildNumber?.let { "Build $it" },
                    ).joinToString(" · "),
                    first = index == 0,
                ) {
                    if (log.markdown.isNotBlank()) {
                        MarkdownBody(log.markdown, Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun ActivityLogsScreen(log: AppActivityLog, onBack: () -> Unit) {
    val entries by log.entries.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = entries.filter { filter == "All" || it.category == filter }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    SettingsScaffold(title = "Logs", onBack = onBack) {
        SettingsSection(title = "App activity", description = "A private, on-device timeline of requests and actions performed in this app. Newest events appear first.", first = true) {
            SettingsGroup {
                listOf("All", "Network", "Settings", "Navigation").forEach { category ->
                    SettingsRow(title = category, selected = filter == category, onClick = { filter = category })
                }
            }
            SettingsActions {
                SettingsButton(text = "Copy logs", kind = ButtonKind.Outlined, enabled = visible.isNotEmpty(), onClick = {
                    val report = visible.joinToString("\n") { entry -> entry.displayTime() + " [" + entry.category + "] " + entry.action + " — " + entry.details + " (" + entry.status + ")" }
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GitHunt activity logs", report))
                })
                SettingsButton(text = "Clear logs", kind = ButtonKind.Outlined, enabled = entries.isNotEmpty(), onClick = { confirmClear = true })
            }
        }
        if (visible.isEmpty()) {
            SettingsSection(title = "Nothing to show", description = "Activity will appear here as you use GitHunt.") {}
        } else {
            SettingsSection(title = visible.size.toString() + " events", description = "Stored locally on this device (up to 300 entries).") {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = scheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        visible.forEachIndexed { index, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = entry.displayTime(),
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    val statusTint = when (entry.status) {
                                        "error", "warning" -> scheme.error
                                        "success" -> scheme.primary
                                        "running" -> scheme.tertiary
                                        else -> scheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = entry.status.uppercase(),
                                        color = statusTint,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "[${entry.category}]",
                                        color = when (entry.category) {
                                            "Network" -> scheme.primary
                                            "Settings" -> scheme.tertiary
                                            "Navigation" -> scheme.secondary
                                            else -> scheme.onSurfaceVariant
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = entry.action,
                                        color = scheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (entry.details.isNotBlank()) {
                                    Text(
                                        text = entry.details,
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = true,
                                    )
                                }
                            }
                            if (index != visible.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog(title = "Clear activity logs?", text = "This removes the locally stored activity history from this device.", confirmLabel = "Clear logs", destructive = true, onConfirm = { log.clear(); confirmClear = false }, onDismiss = { confirmClear = false })
}
