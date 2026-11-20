package com.githunt.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.clips.RepoCollection
import com.githunt.android.data.net.ApiResult
import com.githunt.android.ui.theme.LocalGhMono
import kotlinx.coroutines.launch

private object SheetIcons {
    const val Plus = "M12 5v14M5 12h14"
    const val Folder = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    const val ChevronLeft = "M15 18l-6-6 6-6"
}

private fun repoCountLabel(count: Int) = if (count == 1) "1 repo" else "$count repos"

/**
 * "Save to collection": the website's save sheet. Lists the person's collections ("Watch Later",
 * the default, first) so they choose where a clip goes, with a "Create a new collection" row that
 * makes one and saves straight into it.
 *
 * [onPick] gets the chosen collection and the sheet closes itself; the caller does the actual save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToCollectionSheet(
    repository: ClipsRepository,
    onDismiss: () -> Unit,
    onPick: (RepoCollection) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var collections by remember { mutableStateOf<List<RepoCollection>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // false = the list of collections, true = the "New collection" page.
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var createRunning by remember { mutableStateOf(false) }

    LaunchedEffect(repository, reloadTick) {
        loadError = null
        when (val result = repository.collections()) {
            is ApiResult.Ok -> collections = result.value
            is ApiResult.Err -> loadError = result.message
        }
    }

    fun finish(collection: RepoCollection) {
        onPick(collection)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun submitCreate() {
        val name = newName.trim()
        if (name.isEmpty() || createRunning) return
        createRunning = true
        createError = null
        scope.launch {
            when (val result = repository.createCollection(name)) {
                is ApiResult.Ok -> finish(result.value)
                is ApiResult.Err -> createError = result.message
            }
            createRunning = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.background,
    ) {
        if (!creating) {
            Text(
                text = "Save to collection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                val list = collections
                val error = loadError
                when {
                    error != null -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlineButton(text = "Try again", onClick = { reloadTick++ })
                    }
                    list == null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GhLoadingIndicator(diameter = 32.dp, color = scheme.onSurfaceVariant)
                    }
                    else -> list.forEach { collection ->
                        CollectionPickRow(collection = collection, onClick = { finish(collection) })
                    }
                }
                NewCollectionRow(
                    onClick = {
                        createError = null
                        creating = true
                    },
                )
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        } else {
            NewCollectionPage(
                name = newName,
                onName = { if (it.length <= 60) newName = it },
                error = createError,
                running = createRunning,
                onBack = { creating = false },
                onSubmit = { submitCreate() },
            )
        }
    }
}

/** One collection: cover (newest repo's avatar, or a folder), name with a "Default" pill, repo count. */
@Composable
private fun CollectionPickRow(collection: RepoCollection, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mono = LocalGhMono.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = collection.previewAvatars.firstOrNull { it != null }
            if (avatar != null) {
                ShimmerImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PathIcon(
                    pathData = SheetIcons.Folder,
                    tint = scheme.onSurfaceVariant,
                    iconSize = 22.dp,
                    strokeWidth = 1.6f,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = collection.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (collection.isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Default",
                        fontFamily = mono,
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .border(1.dp, scheme.outline.copy(alpha = 0.65f), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = repoCountLabel(collection.repoCount),
                fontFamily = mono,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun NewCollectionRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tile = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(tile)
                .border(1.dp, scheme.outline, tile),
            contentAlignment = Alignment.Center,
        ) {
            PathIcon(
                pathData = SheetIcons.Plus,
                tint = scheme.onSurfaceVariant,
                iconSize = 20.dp,
                strokeWidth = 2f,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Create a new collection",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
    }
}

/** Name box + "Create and save": makes the collection, then saves the repo into it. */
@Composable
private fun NewCollectionPage(
    name: String,
    onName: (String) -> Unit,
    error: String?,
    running: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Back", role = Role.Button, enabled = !running, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                PathIcon(
                    pathData = SheetIcons.ChevronLeft,
                    tint = scheme.onSurfaceVariant,
                    iconSize = 22.dp,
                    strokeWidth = 2f,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "New collection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
        }
        Spacer(Modifier.height(12.dp))
        GhTextField(
            value = name,
            onValueChange = onName,
            label = "Collection name",
            imeAction = ImeAction.Done,
            onDone = onSubmit,
            enabled = !running,
            modifier = Modifier.focusRequester(focus),
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Create and save",
            onClick = onSubmit,
            enabled = name.isNotBlank() && !running,
            loading = running,
        )
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
