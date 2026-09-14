package com.githunt.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.githunt.android.data.model.Repo
import kotlinx.coroutines.delay

/**
 * Native equivalent of components/RepoCard.js. Tracks dwell time the same
 * way the web version's IntersectionObserver does (>=4s visible counts as a
 * real look; less than that with no other action is a "skip") using a
 * simple LaunchedEffect keyed to composition/visibility, which is the
 * Compose-idiomatic analogue of an IntersectionObserver callback.
 */
@Composable
fun RepoCard(
    repo: Repo,
    onOpenGithub: (Repo) -> Unit,
    onAskAi: (Repo) -> Unit,
    onShare: (Repo) -> Unit,
    onClip: (Repo) -> Unit,
    onNotInterested: (Repo) -> Unit,
    onEngagement: (repoFullName: String, eventType: String, language: String?, topics: List<String>) -> Unit,
    isClipped: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var readmeExpanded by remember { mutableStateOf(false) }
    var hasLoggedDwell by remember { mutableStateOf(false) }

    // Dwell-time tracking: a card visible for >=4s without another action
    // counts as a genuine look, matching lib/interest-scoring.js's weighting.
    LaunchedEffect(repo.full_name) {
        delay(4000)
        if (!hasLoggedDwell) {
            hasLoggedDwell = true
            onEngagement(repo.full_name, "dwell", repo.language, repo.topics)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        // Header: owner avatar + repo name + trending badge + menu
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = repo.owner_avatar_url,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.full_name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                repo._trend_tag?.let {
                    Text(
                        text = when (it) {
                            "hot" -> "🔥 Trending"
                            "rising" -> "📈 Rising"
                            "new_and_starring" -> "✨ New & starring"
                            else -> it
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Not interested") }, onClick = {
                        menuExpanded = false
                        onNotInterested(repo)
                    })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        menuExpanded = false
                        onEngagement(repo.full_name, "share", repo.language, repo.topics)
                        onShare(repo)
                    })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        repo.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Spacer(Modifier.height(10.dp))
        }

        // Language + stats row
        Row(verticalAlignment = Alignment.CenterVertically) {
            repo.language?.let {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            Text(formatCount(repo.stargazers_count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            Text(formatCount(repo.forks_count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (repo.topics.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(repo.topics.take(6)) { topic ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(topic, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!repo.readme_snippet.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = {
                readmeExpanded = !readmeExpanded
                if (readmeExpanded) onEngagement(repo.full_name, "expand_readme", repo.language, repo.topics)
            }) {
                Text(if (readmeExpanded) "Hide README" else "Show README")
            }
            if (readmeExpanded) {
                Text(
                    repo.readme_snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action row: GitHub / Ask AI / Clip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = {
                onEngagement(repo.full_name, "open_github", repo.language, repo.topics)
                onOpenGithub(repo)
            }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("GitHub")
            }
            OutlinedButton(onClick = {
                onEngagement(repo.full_name, "ask_ai", repo.language, repo.topics)
                onAskAi(repo)
            }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ask AI")
            }
            IconButton(onClick = {
                onEngagement(repo.full_name, if (isClipped) "unclip" else "clip", repo.language, repo.topics)
                onClip(repo)
            }) {
                Icon(
                    if (isClipped) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Clip",
                )
            }
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
