package com.githunt.android.ui.compose

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.githunt.android.ui.auth.AuthViewModel

/**
 * Native equivalent of app/compose/page.js's toolbar: image picker, file
 * picker (metadata only, matching the web app), link entry, and repo
 * attach (via GET /api/github/repo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onDismiss: () -> Unit,
    onPosted: () -> Unit,
    viewModel: ComposeViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val user by authViewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var showLinkRow by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var showRepoRow by remember { mutableStateOf(false) }

    val needsVerification = user?.email != null && !user!!.email_verified

    LaunchedEffect(uiState.didPost) {
        if (uiState.didPost) onPosted()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.pickImage(it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else "file"
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    viewModel.setFile(name, size)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New post") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.post() }, enabled = uiState.canPost) {
                        Text(if (uiState.isPosting) "Posting…" else "Post")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (needsVerification) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Confirm your email before posting.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::setText,
                placeholder = { Text("What's worth sharing?") },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 120.dp),
            )

            // --- Attachment previews -------------------------------------
            uiState.image?.let { img ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = img.dataUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.clearImage() }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove image")
                    }
                }
            }
            uiState.file?.let { file ->
                Spacer(Modifier.height(10.dp))
                AttachmentChip(label = file.name, onRemove = { viewModel.clearFile() })
            }
            uiState.link?.let { link ->
                Spacer(Modifier.height(10.dp))
                AttachmentChip(label = link.domain, onRemove = { viewModel.clearLink() })
            }
            uiState.repo?.let { repo ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = repo.owner_avatar,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(repo.owner ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("/${repo.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearRepo() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove repo", modifier = Modifier.size(16.dp))
                        }
                    }
                    repo.description?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (uiState.imageTooLarge) {
                Text("That image is too large.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // --- Link entry row --------------------------------------------
            if (showLinkRow) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        placeholder = { Text("Paste a link…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.addLink(linkInput)
                        linkInput = ""
                        showLinkRow = false
                    }) { Text("Add") }
                }
            }

            // --- Repo attach row --------------------------------------------
            if (showRepoRow) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = uiState.repoLookupInput,
                        onValueChange = viewModel::setRepoLookupInput,
                        placeholder = { Text("owner/repo or a GitHub URL…") },
                        singleLine = true,
                        enabled = !uiState.isLookingUpRepo,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.lookupRepo(); showRepoRow = false }, enabled = !uiState.isLookingUpRepo) {
                        Text(if (uiState.isLookingUpRepo) "…" else "Add")
                    }
                }
                uiState.repoLookupError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (uiState.emailUnverified) {
                Text(
                    "Confirm your email before posting — check your inbox or resend the link from your profile.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (uiState.isPosting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Add image")
                }
                IconButton(onClick = { filePicker.launch("*/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Add file")
                }
                IconButton(onClick = { showLinkRow = !showLinkRow }) {
                    Icon(Icons.Default.Link, contentDescription = "Add link")
                }
                IconButton(onClick = { showRepoRow = !showRepoRow }) {
                    Icon(Icons.Default.Code, contentDescription = "Attach a repo")
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(18.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
        }
    }
}
