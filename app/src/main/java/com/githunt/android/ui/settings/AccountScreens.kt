package com.githunt.android.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.githunt.android.data.auth.User
import com.githunt.android.data.settings.DeviceSession
import com.githunt.android.ui.components.NavIcons
import com.githunt.android.ui.components.PathIcon

@Composable
fun AccountScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val openSite = rememberSiteOpener()
    SettingsScaffold(title = "Account", onBack = onBack) {
        SettingsSection(
            title = "Account settings",
            description = "Manage your profile, password and security.",
            first = true,
        ) {
            // There is no native profile editor yet, so this opens the website's.
            SettingsRow(
                title = "Edit profile",
                subtitle = "Name, username, bio, photos",
                icon = NavIcons.Edit,
                onClick = { openSite("profile/edit") },
                trailing = { OpenTrailing() },
            )
            SettingsRow(
                title = "Change password",
                icon = SettingsIcons.Lock,
                onClick = { onNavigate("password") },
                trailing = { ChevronTrailing() },
            )
            SettingsRow(
                title = "Security",
                icon = SettingsIcons.Shield,
                onClick = { onNavigate("security") },
                trailing = { ChevronTrailing() },
            )
        }
    }
}

@Composable
fun PasswordScreen(user: User, vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.password.collectAsStateWithLifecycle()
    val open = rememberWebOpener()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.resetPassword() }

    SettingsScaffold(title = "Change password", onBack = onBack) {
        if (user.hasGoogle) {
            // Google owns this credential, so say so instead of showing a form that can't work.
            SettingsSection(
                title = "Change password",
                description = "Google manages the password for this account.",
                first = true,
            ) {
                SettingsCallout(
                    iconPath = SettingsIcons.Lock,
                    title = "You sign in with Google",
                    body = "You can't change your password for your Google account in here. Since this account " +
                        "uses \"Sign in with Google\", Google manages that credential. Head to your Google " +
                        "Account settings to change it instead.",
                )
                SettingsActions {
                    SettingsButton(
                        text = "Open Google Account security",
                        onClick = { open("https://myaccount.google.com/security") },
                        kind = ButtonKind.Outlined,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            SettingsSection(
                title = "Change password",
                description = "Update the password you use to sign in.",
                first = true,
            ) {
                if (state.success) {
                    SettingsCallout(
                        iconPath = NavIcons.Check,
                        title = "Password updated",
                        body = "Your password has been changed.",
                        iconTint = SuccessGreen,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsField(
                            label = "Current password",
                            value = current,
                            onValueChange = { current = it },
                            password = true,
                            enabled = !state.saving,
                        )
                        SettingsField(
                            label = "New password",
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            hint = "At least 8 characters.",
                            password = true,
                            enabled = !state.saving,
                        )
                        SettingsField(
                            label = "Confirm new password",
                            value = confirm,
                            onValueChange = { confirm = it },
                            password = true,
                            enabled = !state.saving,
                        )
                    }
                    state.error?.let { SettingsNote(text = it, error = true) }
                    SettingsActions {
                        SettingsButton(
                            text = if (state.saving) "Updating\u2026" else "Update password",
                            onClick = { vm.changePassword(current, newPassword, confirm) },
                            loading = state.saving,
                            enabled = current.isNotEmpty() && newPassword.isNotEmpty() && confirm.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityScreen(
    user: User,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by vm.security.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<DeviceSession?>(null) }
    var pendingAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSessions() }

    SettingsScaffold(title = "Security", onBack = onBack) {
        SettingsSection(title = "Sign-in method", description = "How you sign in to GitHunt.", first = true) {
            if (user.hasGoogle) {
                SettingsRow(
                    title = "Google",
                    subtitle = "You're signed in through your Google account",
                    icon = SettingsIcons.Shield,
                )
            } else {
                SettingsRow(
                    title = "Password",
                    subtitle = "You sign in with a username and password",
                    icon = SettingsIcons.Lock,
                )
            }
        }

        SettingsSection(
            title = "Connected accounts",
            description = "Manage your GitHub connection from Edit profile.",
        ) {
            SettingsRow(
                title = "GitHub",
                subtitle = user.githubUsername?.let { "Connected as $it" } ?: "Not connected",
                icon = NavIcons.Code,
            )
        }

        SettingsSection(title = "Email", description = "The address tied to your account.") {
            SettingsRow(
                title = user.email ?: "No email on file",
                subtitle = if (user.emailVerified) "Verified" else "Not verified",
                icon = SettingsIcons.Mail,
                trailing = if (user.emailVerified) {
                    { PathIcon(pathData = NavIcons.Check, tint = SuccessGreen, iconSize = 22.dp) }
                } else {
                    null
                },
            )
        }

        SettingsSection(
            title = "Where you're signed in",
            description = "These are the devices currently signed in to your account. Logging one out ends that session right away.",
        ) {
            val sessions = state.sessions
            if (sessions == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.padding(0.dp)) }
            } else {
                if (sessions.isEmpty()) SettingsNote("No active sign-ins found.")
                sessions.forEach { session ->
                    val ago = activeAgo(session.lastSeenAt)
                    SettingsRow(
                        title = sessionTitle(session),
                        subtitle = listOf(if (session.isCurrent) "This device" else "", ago)
                            .filter { it.isNotEmpty() }
                            .joinToString(" \u00B7 ")
                            .ifEmpty { null },
                        icon = SettingsIcons.Phone,
                        trailing = {
                            OutlinedButton(
                                onClick = { pending = session },
                                enabled = state.revokingId == null,
                            ) { Text("Log out", fontWeight = FontWeight.SemiBold) }
                        },
                    )
                }
                if (sessions.size > 1) {
                    SettingsRow(
                        title = "Log out other devices",
                        subtitle = "Keep only this device signed in",
                        icon = com.githunt.android.ui.components.AppIcons.SignOut,
                        danger = true,
                        enabled = state.revokingId == null,
                        onClick = { pendingAll = true },
                    )
                }
            }
            state.error?.let { SettingsNote(text = it, error = true) }
        }
    }

    pending?.let { target ->
        ConfirmDialog(
            title = if (target.isCurrent) "Log out this device?" else "Log out ${target.label}?",
            text = if (target.isCurrent) {
                "You're revoking the session you're using right now. You'll be signed out immediately and need to log in again."
            } else {
                "That device will be signed out immediately."
            },
            confirmLabel = if (state.revokingId != null) "Logging out\u2026" else "Log out",
            destructive = true,
            onConfirm = {
                vm.revokeSession(target, onSignedOut = onSignOut, onDone = { pending = null })
            },
            onDismiss = { if (state.revokingId == null) pending = null },
        )
    }

    if (pendingAll) {
        ConfirmDialog(
            title = "Log out other devices?",
            text = "Every other signed-in device will be logged out immediately. This device stays signed in.",
            confirmLabel = if (state.revokingId != null) "Logging out\u2026" else "Log out",
            destructive = true,
            onConfirm = { vm.revokeOtherSessions(onDone = { pendingAll = false }) },
            onDismiss = { if (state.revokingId == null) pendingAll = false },
        )
    }
}

@Composable
fun ManageAccountScreen(
    user: User,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by vm.delete.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.resetDelete() }

    val matches = typed.trim().lowercase() == user.username.lowercase()

    SettingsScaffold(title = "Manage Account", onBack = onBack) {
        SettingsSection(
            title = "Delete account",
            description = "Permanently remove your account and profile.",
            first = true,
        ) {
            if (!confirming) {
                SettingsRow(
                    title = "Delete account",
                    subtitle = "This can't be undone",
                    icon = SettingsIcons.Delete,
                    danger = true,
                    onClick = { confirming = true },
                )
            } else {
                SettingsBody(
                    text = "This permanently deletes your account and profile. It can't be undone. " +
                        "Type ${user.username} to confirm.",
                )
                SettingsField(
                    label = "Username",
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = user.username,
                    enabled = !state.deleting,
                )
                state.error?.let { SettingsNote(text = it, error = true) }
                SettingsActions {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsButton(
                            text = "Cancel",
                            onClick = {
                                confirming = false
                                typed = ""
                                vm.resetDelete()
                            },
                            kind = ButtonKind.Outlined,
                            enabled = !state.deleting,
                            modifier = Modifier.weight(1f),
                        )
                        SettingsButton(
                            text = if (state.deleting) "Deleting\u2026" else "Delete permanently",
                            onClick = { vm.deleteAccount(onDeleted = onSignOut) },
                            kind = ButtonKind.Danger,
                            loading = state.deleting,
                            enabled = matches,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
