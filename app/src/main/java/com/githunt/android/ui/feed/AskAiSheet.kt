package com.githunt.android.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githunt.android.data.model.Repo

/**
 * Native equivalent of the web app's per-repo "Ask AI" panel
 * (app/api/ai-chat/route.js). One AskAiViewModel instance per sheet, so
 * history resets when the sheet is dismissed and reopened — same as the
 * web client, which keeps no server-side conversation state either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiSheet(
    repo: Repo,
    onDismiss: () -> Unit,
    viewModel: AskAiViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var question by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 560.dp).padding(16.dp)) {
            Text("Ask AI about ${repo.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.messages) { turn ->
                    val isUser = turn.role == "user"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(0.85f),
                        ) {
                            Text(
                                turn.content,
                                color = if (isUser) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (uiState.isSending) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterStart) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("What does this repo do?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (question.isNotBlank() && !uiState.isSending) {
                        viewModel.send(question, repo)
                        question = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
