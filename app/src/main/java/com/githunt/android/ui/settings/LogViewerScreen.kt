package com.githunt.android.ui.settings

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.githunt.android.util.AppLog
import kotlinx.coroutines.launch

/**
 * On-device log viewer/exporter. Exists so the user can capture and share
 * startup + runtime logs (network requests, auth failures, crashes)
 * without needing a computer or android.permission.READ_LOGS -- see
 * util/AppLog.kt for why that permission can't be granted from the phone
 * alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Re-read the buffer on a short tick so lines written by in-flight
    // requests (e.g. right after tapping "Log in") show up without the
    // user having to back out and re-enter this screen.
    var lines by remember { mutableStateOf(AppLog.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            lines = AppLog.snapshot()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Auto-scroll to the newest line when new lines arrive, unless the user
    // has scrolled up to read something -- don't yank them back down.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !listState.canScrollForward) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                androidx.compose.ui.platform.ClipEntry(
                                    ClipData.newPlainText("GitHunt logs", AppLog.exportText()),
                                ),
                            )
                        }
                    }) {
                        Icon(Icons.Default.Delete.let { androidx.compose.material.icons.Icons.Default.ContentCopy }, contentDescription = "Copy all")
                    }
                    IconButton(onClick = {
                        val file = AppLog.exportFile() ?: return@IconButton
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share GitHunt logs"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share log file")
                    }
                    IconButton(onClick = {
                        AppLog.clear()
                        lines = AppLog.snapshot()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { padding ->
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No log lines yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colorForLine(line),
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun colorForLine(line: String) = when {
    " E/" in line -> MaterialTheme.colorScheme.error
    " W/" in line -> androidx.compose.ui.graphics.Color(0xFFE8A33D)
    else -> MaterialTheme.colorScheme.onSurface
}
