package com.githunt.android.ui.clips

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.clips.RepoCollection
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.ui.PageMessage
import com.githunt.android.ui.components.GhTextField
import com.githunt.android.ui.components.PathIcon
import com.githunt.android.ui.components.RepoCard
import com.githunt.android.ui.components.RepoCardSkeleton
import com.githunt.android.ui.components.ShimmerBlock
import com.githunt.android.ui.components.ShimmerImage
import com.githunt.android.ui.components.SubPageBar
import kotlinx.coroutines.launch

/** Stroke icons (24 x 24 viewport) for the collections page, matching the website's. */
private object CollectionIcons {
    const val Plus = "M12 5v14M5 12h14"
    const val Trash = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"
    const val Folder = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
}

private fun repoCountLabel(count: Int) = if (count == 1) "1 repo" else "$count repos"

/**
 * Clipped repos, like the website's /clipped page: a two-column grid of the person's collections
 * ("Watch Later" first, then their own), each with a 2x2 cover of the newest repos' avatars and a
 * repo count. Tapping one opens just that collection's repos. The + creates a collection and the
 * trash on a collection deletes it (its repos move to Watch Later).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClippedReposScreen(repository: ClipsRepository, onBack: () -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openName by rememberSaveable { mutableStateOf("") }
    var collections by remember { mutableStateOf<List<RepoCollection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, reloadTick) {
        when (val result = repository.collections()) {
            is ApiResult.Ok -> {
                collections = result.value
                error = null
            }
            is ApiResult.Err -> error = result.message
        }
        refreshing = false
    }

    val currentId = openId
    if (currentId != null) {
        // Back from one collection returns to the grid (and refreshes its counts and covers).
        val closeCollection: () -> Unit = {
            openId = null
            reloadTick++
        }
        BackHandler(onBack = closeCollection)
        CollectionScreen(
            repository = repository,
            id = currentId,
            initialName = openName,
            onBack = closeCollection,
        )
    } else {
        CollectionsPage(
            repository = repository,
            collections = collections,
            error = error,
            refreshing = refreshing,
            onBack = onBack,
            onRefresh = {
                refreshing = true
                reloadTick++
            },
            onRetry = {
                error = null
                reloadTick++
            },
            onOpen = {
                openName = it.name
                openId = it.id
            },
            onCreated = { created -> collections = collections.orEmpty() + created },
            onDeleted = { deleted ->
                // The server moves a deleted collection's repos into Watch Later, so fold the count in.
                collections = collections.orEmpty()
                    .filterNot { it.id == deleted.id }
                    .map { if (it.isDefault) it.copy(repoCount = it.repoCount + deleted.repoCount) else it }
                reloadTick++
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsPage(
    repository: ClipsRepository,
    collections: List<RepoCollection>?,
    error: String?,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (RepoCollection) -> Unit,
    onCreated: (RepoCollection) -> Unit,
    onDeleted: (RepoCollection) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    var deleteTarget by remember { mutableStateOf<RepoCollection?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    fun submitCreate() {
        val name = createName.trim()
        if (name.isEmpty() || creating) return
        creating = true
        createError = null
        scope.launch {
            when (val result = repository.createCollection(name)) {
                is ApiResult.Ok -> {
                    onCreated(result.value)
                    showCreate = false
                    createName = ""
                }
                is ApiResult.Err -> createError = result.message
            }
            creating = false
        }
    }

    fun confirmDelete() {
        val target = deleteTarget ?: return
        if (deleting) return
        deleting = true
        deleteError = null
        scope.launch {
            when (val result = repository.deleteCollection(target.id)) {
                is ApiResult.Ok -> {
                    onDeleted(target)
                    deleteTarget = null
                }
                is ApiResult.Err -> deleteError = result.message
            }
            deleting = false
        }
    }

    val list = collections
    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
      Column(modifier = Modifier.fillMaxSize()) {
        SubPageBar(
            title = "Clipped repos",
            onBack = onBack,
            subtitle = list?.let { repoCountLabel(it.sumOf { c -> c.repoCount }) },
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    list == null && error == null -> items(4, key = { index -> "skeleton-$index" }) {
                        CollectionCardSkeleton()
                    }
                    list == null -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        PageMessage(
                            title = "Couldn't load your collections",
                            body = error.orEmpty(),
                            actionLabel = "Try again",
                            onAction = onRetry,
                        )
                    }
                    list.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        PageMessage(
                            title = "Nothing clipped yet",
                            body = "Tap the bookmark on a repo in Discover to save it here. Only you can see this.",
                        )
                    }
                    else -> {
                        items(list, key = { it.id }) { collection ->
                            CollectionCard(
                                collection = collection,
                                onClick = { onOpen(collection) },
                                onDelete = if (collection.isDefault) {
                                    null
                                } else {
                                    {
                                        deleteError = null
                                        deleteTarget = collection
                                    }
                                },
                            )
                        }
                        if (list.sumOf { it.repoCount } == 0) {
                            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                                PageMessage(
                                    title = "Nothing clipped yet",
                                    body = "Tap the bookmark on a repo in Discover to save it here. Only you can see this.",
                                )
                            }
                        }
                    }
                }
            }
        }
      }
      FloatingActionButton(
          onClick = {
              createError = null
              showCreate = true
          },
          modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 24.dp, bottom = 24.dp)
              .navigationBarsPadding()
              .size(68.dp),
          shape = RoundedCornerShape(24.dp),
          containerColor = scheme.primaryContainer,
          contentColor = scheme.onPrimaryContainer,
      ) {
          PathIcon(
              pathData = CollectionIcons.Plus,
              tint = scheme.onPrimaryContainer,
              iconSize = 28.dp,
              strokeWidth = 2f,
          )
      }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!creating) showCreate = false },
            title = { Text("New collection") },
            text = {
                Column {
                    GhTextField(
                        value = createName,
                        onValueChange = { if (it.length <= 60) createName = it },
                        label = "Collection name",
                        imeAction = ImeAction.Done,
                        onDone = { submitCreate() },
                        enabled = !creating,
                    )
                    val message = createError
                    if (message != null) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitCreate() },
                    enabled = createName.isNotBlank() && !creating,
                ) { Text(if (creating) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }, enabled = !creating) { Text("Cancel") }
            },
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = {
                Text(
                    deleteError ?: if (target.repoCount > 0) {
                        "${repoCountLabel(target.repoCount)} will move to Watch Later. This can't be undone."
                    } else {
                        "This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete() }, enabled = !deleting) {
                    Text(if (deleting) "Deleting…" else "Delete", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !deleting) { Text("Cancel") }
            },
        )
    }
}

/** One collection: square cover on top, name (+ "Default" pill) and repo count underneath. */
@Composable
private fun CollectionCard(
    collection: RepoCollection,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val coverShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(coverShape),
            ) {
                CoverGrid(collection.previewAvatars)
            }
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
                Text(
                    text = collection.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repoCountLabel(collection.repoCount),
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClickLabel = "Delete ${collection.name}", role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                PathIcon(
                    pathData = CollectionIcons.Trash,
                    tint = Color.White.copy(alpha = 0.9f),
                    iconSize = 14.dp,
                    strokeWidth = 1.8f,
                )
            }
        }
    }
}

/** Cover art: a folder when empty, one big avatar for a single repo, otherwise a 2x2 grid. */
@Composable
private fun CoverGrid(avatars: List<String?>) {
    val scheme = MaterialTheme.colorScheme
    val urls = avatars.filterNotNull()
    when {
        avatars.isEmpty() || urls.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            PathIcon(
                pathData = CollectionIcons.Folder,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                iconSize = 30.dp,
                strokeWidth = 1.6f,
            )
        }
        avatars.size == 1 -> AvatarCell(urls.first(), Modifier.fillMaxSize())
        else -> Column(
            modifier = Modifier.fillMaxSize().background(scheme.outline.copy(alpha = 0.55f)),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    for (col in 0..1) {
                        AvatarCell(avatars.getOrNull(row * 2 + col), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarCell(url: String?, modifier: Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        if (url != null) {
            ShimmerImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CollectionCardSkeleton() {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape),
    ) {
        ShimmerBlock(Modifier.fillMaxWidth().aspectRatio(1f))
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBlock(Modifier.height(14.dp).width(80.dp).clip(RoundedCornerShape(4.dp)))
            ShimmerBlock(Modifier.height(12.dp).width(56.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

/**
 * One collection's repos: the same [RepoCard] the feed uses, each with its bookmark filled.
 * Tapping the bookmark unclips it and the card leaves the list straight away; if the server
 * refuses, it comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(
    repository: ClipsRepository,
    id: String,
    initialName: String,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(id) { mutableStateOf(initialName) }
    var items by remember(id) { mutableStateOf<List<RepoItem>?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var notFound by remember(id) { mutableStateOf(false) }
    var refreshing by remember(id) { mutableStateOf(false) }
    var reloadTick by remember(id) { mutableIntStateOf(0) }

    LaunchedEffect(repository, id, reloadTick) {
        when (val result = repository.inCollection(id)) {
            is ApiResult.Ok -> {
                items = result.value.items
                if (result.value.name.isNotBlank()) name = result.value.name
                error = null
                notFound = false
            }
            is ApiResult.Err -> {
                if (result.code == 404) notFound = true else error = result.message
            }
        }
        refreshing = false
    }

    fun unclip(repo: RepoItem) {
        val before = items ?: return
        items = before.filterNot { it.fullName == repo.fullName }
        scope.launch {
            if (repository.remove(repo.fullName) is ApiResult.Err) {
                items = before
                Toast.makeText(context, "Couldn't remove that clip. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val list = items
    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        SubPageBar(
            title = name.ifBlank { "Collection" },
            onBack = onBack,
            subtitle = list?.let { repoCountLabel(it.size) },
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reloadTick++
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    notFound -> item(key = "missing") {
                        PageMessage(
                            title = "Collection not found",
                            body = "This collection doesn't exist anymore.",
                        )
                    }
                    list == null && error == null -> items(3, key = { index -> "skeleton-$index" }) {
                        RepoCardSkeleton()
                    }
                    list == null -> item(key = "error") {
                        PageMessage(
                            title = "Couldn't load this collection",
                            body = error.orEmpty(),
                            actionLabel = "Try again",
                            onAction = {
                                error = null
                                reloadTick++
                            },
                        )
                    }
                    list.isEmpty() -> item(key = "empty") {
                        PageMessage(
                            title = "Nothing here yet",
                            body = "Repos you save into this collection show up here. Only you can see this.",
                        )
                    }
                    else -> {
                        items(list, key = { it.fullName }) { repo ->
                            RepoCard(
                                item = repo,
                                clipped = true,
                                onClipToggle = { unclip(repo) },
                            )
                        }
                        item(key = "footer") { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
