package com.githunt.android.ui.settings

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
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
import com.githunt.android.BuildConfig
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
    val openSite = rememberSiteOpener()
    SettingsScaffold(title = "About GitHunt", onBack = onBack) {
        SettingsSection(title = "About GitHunt", description = "Learn how GitHunt works and what's new.", first = true) {
            SettingsRow(title = "What is this", subtitle = "What GitHunt is", icon = SettingsIcons.Info, onClick = { onNavigate("what") }, trailing = { ChevronTrailing() })
            SettingsRow(title = "How to use", subtitle = "Posting, discovering, and more", icon = SettingsIcons.Book, onClick = { onNavigate("howto") }, trailing = { ChevronTrailing() })
            SettingsRow(title = "Info", subtitle = "Version, developer, and request a feature", icon = SettingsIcons.Info, onClick = { onNavigate("info") }, trailing = { ChevronTrailing() })
            SettingsRow(title = "Logs", subtitle = "What's changed, most recent first", icon = SettingsIcons.Book, onClick = { onNavigate("logs") }, trailing = { ChevronTrailing() })
        }
        SettingsSection(title = "Legal", description = "Review GitHunt's policies.") {
            SettingsRow(title = "Privacy Policy", icon = SettingsIcons.File, onClick = { openSite("privacy") }, trailing = { OpenTrailing() })
            SettingsRow(title = "Terms of Service", icon = SettingsIcons.File, onClick = { openSite("terms") }, trailing = { OpenTrailing() })
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
            SettingsRow(title = "Post something", subtitle = "Use the create button to share text, an image, a file, a link, or a GitHub repository.")
            SettingsRow(title = "Search the feed", subtitle = "Use feed search to find posts and projects by keyword.")
            SettingsRow(title = "Discover repos", subtitle = "Open Discover to browse trending or top GitHub repositories with live stats.")
            SettingsRow(title = "Clip a repo", subtitle = "Save a repository privately to your Clipped list for later.")
            SettingsRow(title = "Like and comment", subtitle = "Like posts and open a post to join the conversation.")
            SettingsRow(title = "Connect GitHub", subtitle = "Connect a GitHub account from your profile editor so people can find your work.")
        }
        SettingsSection(title = "Tutorial", description = "A quick reminder of the main app areas.") {
            SettingsRow(title = "Home", subtitle = "Your feed of posts and repository activity.")
            SettingsRow(title = "Discover", subtitle = "Explore repositories and developers.")
            SettingsRow(title = "Profile", subtitle = "Manage your profile, posts, follows, repositories, and clipped items.")
        }
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
            SettingsRow(title = "Share", subtitle = "Post images, files, and links to a feed with activity from people you follow.", icon = SettingsIcons.Image)
            SettingsRow(title = "Discover", subtitle = "Browse trending and top GitHub repositories with stars, forks, and live stats.", icon = SettingsIcons.Info)
            SettingsRow(title = "Translate", subtitle = "Translate repository descriptions and READMEs in the app.", icon = SettingsIcons.Translate)
            SettingsRow(title = "Your account, your data", subtitle = "Your profile, posts, and follows stay tied to your account.", icon = SettingsIcons.Shield)
        }
        SettingsSection(title = "Powered by the GitHub API", description = "Repository discovery, stats, and profile data are fetched live from GitHub. You can connect your own token in API Connection.") {}
        SettingsSection(title = "Developer", description = "Get in touch or follow along.") {
            SettingsRow(title = "Aadin Kc", subtitle = "Kerala, India")
            SettingsRow(title = "GitHub", subtitle = "@heyitsaadin", icon = SettingsIcons.Info, onClick = { open("https://github.com/heyitsaadin") }, trailing = { OpenTrailing() })
            SettingsRow(title = "LinkedIn", subtitle = "Aadin Kc", icon = SettingsIcons.LinkedIn, onClick = { open("https://www.linkedin.com/in/aadin-kc-128bb3371") }, trailing = { OpenTrailing() })
            SettingsRow(title = "X (Twitter)", subtitle = "@its_aadin", icon = SettingsIcons.XMark, onClick = { open("https://x.com/its_aadin") }, trailing = { OpenTrailing() })
            SettingsRow(title = "Email", subtitle = "hey.itsaadin@gmail.com", icon = SettingsIcons.Mail, onClick = { open("mailto:hey.itsaadin@gmail.com") }, trailing = { OpenTrailing() })
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
    SettingsScaffold(title = "Changelog", onBack = onBack) {
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
                    if (log.markdown.isNotBlank()) ChangelogMarkdown(log.markdown)
                }
            }
        }
    }
}

@Composable
private fun ChangelogMarkdown(markdown: String) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurfaceVariant.toArgb()
    val linkColor = scheme.primary.toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val markwon = remember(context, textColor, linkColor, codeBackground) {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor)
                        .codeTextColor(textColor)
                        .codeBackgroundColor(codeBackground)
                        .codeBlockTextColor(textColor)
                        .codeBlockBackgroundColor(codeBackground)
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 14f
                setLineSpacing(0f, 1.2f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            val key = markdown to markwon
            if (view.tag != key) {
                view.tag = key
                markwon.setMarkdown(view, markdown)
            }
        },
    )
}
