package com.githunt.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githunt.android.data.repo.ApiResult
import com.githunt.android.data.repo.AuthRepository
import com.githunt.android.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onConnectGithub: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var confirmUsername by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val user by authViewModel.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsRow(title = "Edit profile") { }
            SettingsRow(title = "Personalization") { }
            SettingsRow(
                title = if (user?.github_username != null) "GitHub: @${user?.github_username}" else "Connect GitHub",
                onClick = onConnectGithub,
            )
            SettingsRow(title = "About") { }
            Divider()
            SettingsRow(title = "Log out", isDestructive = false) {
                authViewModel.logout()
                onLoggedOut()
            }
            SettingsRow(title = "Delete account", isDestructive = true) {
                showDeleteConfirm = true
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account") },
            text = {
                Column {
                    Text("This permanently deletes your account. Type your username (\"${user?.username}\") to confirm.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmUsername,
                        onValueChange = { confirmUsername = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (confirmUsername.trim() == user?.username) {
                            scope.launch {
                                when (val result = AuthRepository.getInstance(context).deleteAccount()) {
                                    is ApiResult.Success -> {
                                        showDeleteConfirm = false
                                        onLoggedOut()
                                    }
                                    is ApiResult.Failure -> deleteError = result.message
                                }
                            }
                        } else {
                            deleteError = "Username doesn't match."
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsRow(title: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(title, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        },
        trailingContent = {
            if (!isDestructive) Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickableRow(onClick),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
