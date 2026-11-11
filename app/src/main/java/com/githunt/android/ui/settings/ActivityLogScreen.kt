package com.githunt.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.githunt.android.data.logs.ActivityLog
import com.githunt.android.data.logs.LogCategory
import com.githunt.android.data.logs.LogEntry
import com.githunt.android.data.logs.LogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Material "terminal" icon (24 x 24), used for the Logs row in Settings. */
const val LogsIconPath =
    "M20 4H4c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.89-2-2-2zm0 14H4V8h16v10zm-2-1h-6v-2h6v2zM7.5 17l-1.41-1.41L8.67 13l-2.59-2.59L7.5 9l4 4-4 4z"

private const val PAGE_SIZE = 100

private val RowTime: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d \u00B7 HH:mm:ss")
private val ExportTime: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
private val Amber = Color(0xFFF59E0B)

private fun LogEntry.at(formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(formatter)

/**
 * Settings > Logs. A live, on-device record of what the app is doing: network requests, settings
 * changes, sign-ins, navigation and app open / close. Newest first, filterable and searchable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val entries by ActivityLog.entries.collectAsState()
    var filter by remember { mutableStateOf<LogCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(PAGE_SIZE) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val q = query.trim()
    val shown = remember(entries, filter, q) {
        entries.asReversed().filter { entry ->
            (filter == null || entry.category == filter) &&
                (q.isEmpty() || entry.message.contains(q, ignoreCase = true))
        }
    }

    SettingsScaffold(title = "Logs", onBack = onBack) {
        SettingsSection(
            title = "Activity log",
            description = "Everything GitHunt does on this phone: requests, setting changes, sign-ins and app activity. " +
                "It stays on this device. Passwords, tokens and message contents are never recorded.",
            first = true,
        ) {
            SettingsField(
                label = "Search logs",
                value = query,
                onValueChange = { query = it; limit = PAGE_SIZE },
                maxLength = 100,
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null; limit = PAGE_SIZE },
                    label = { Text("All") },
                )
                LogCategory.values().forEach { category ->
                    FilterChip(
                        selected = filter == category,
                        onClick = { filter = if (filter == category) null else category; limit = PAGE_SIZE },
                        label = { Text(category.label) },
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            SettingsNote(
                if (entries.isEmpty()) "Nothing logged yet. Use the app and your activity will show up here."
                else "No log entries match.",
            )
        } else {
            SettingsNote("Showing ${minOf(limit, shown.size)} of ${shown.size} entries, newest first.")
            SettingsGroup {
                shown.take(limit).forEach { entry ->
                    SettingsRow(
                        title = entry.message,
                        subtitle = "${entry.at(RowTime)} \u00B7 ${entry.category.label}",
                        leading = { LevelDot(entry.level) },
                    )
                }
            }
            if (shown.size > limit) {
                SettingsActions {
                    SettingsButton(
                        text = "Show more",
                        kind = ButtonKind.Outlined,
                        onClick = { limit += PAGE_SIZE },
                    )
                }
            }
        }

        SettingsActions(modifier = Modifier.padding(top = 16.dp)) {
            SettingsButton(
                text = if (copied) "Copied" else "Copy logs",
                kind = ButtonKind.Outlined,
                enabled = shown.isNotEmpty(),
                onClick = {
                    val text = shown.asReversed().joinToString("\n") { e ->
                        "${e.at(ExportTime)} [${e.category.label}] ${e.level.name.uppercase()} ${e.message}"
                    }
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
            )
            SettingsButton(
                text = "Clear logs",
                kind = ButtonKind.Danger,
                enabled = entries.isNotEmpty(),
                onClick = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear logs?",
            text = "This deletes the activity log stored on this phone.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                copied = false
                ActivityLog.clear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun LevelDot(level: LogLevel) {
    val scheme = MaterialTheme.colorScheme
    val color = when (level) {
        LogLevel.Info -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
        LogLevel.Warn -> Amber
        LogLevel.Error -> scheme.error
    }
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
