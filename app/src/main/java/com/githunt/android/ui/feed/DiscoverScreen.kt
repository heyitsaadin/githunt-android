package com.githunt.android.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.githunt.android.data.model.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var askAiRepo by remember { mutableStateOf<Repo?>(null) }
    val clippedRepos = remember { mutableStateOf(setOf<String>()) }

    // Infinite scroll: load next page once the user nears the end of the
    // currently loaded list, mirroring the web app's scroll-triggered fetch.
    LaunchedEffect(listState, uiState.repos.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= uiState.repos.size - 4) {
                    viewModel.loadNextPage()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Discover") })
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(uiState.repos, key = { it.full_name }) { repo ->
                    RepoCard(
                        repo = repo,
                        isClipped = clippedRepos.value.contains(repo.full_name),
                        onOpenGithub = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.html_url)))
                        },
                        onAskAi = { askAiRepo = it },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${it.full_name} — ${it.html_url}")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        onClip = {
                            clippedRepos.value = if (clippedRepos.value.contains(it.full_name)) {
                                clippedRepos.value - it.full_name
                            } else {
                                clippedRepos.value + it.full_name
                            }
                        },
                        onNotInterested = { viewModel.markNotInterested(it) },
                        onEngagement = viewModel::recordEngagement,
                    )
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.errorMessage?.let { message ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    askAiRepo?.let { repo ->
        AskAiSheet(repo = repo, onDismiss = { askAiRepo = null })
    }
}
