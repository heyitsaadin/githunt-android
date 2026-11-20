package com.githunt.android.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.githunt.android.GitHuntApp
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.clips.RepoCollection
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which repos on screen are clipped, and the tap that flips one. It does what DiscoverViewModel does
 * for Discover (flip at once, flip back if the server says no), for screens that have no ViewModel
 * of their own, like the Home feed.
 *
 * Only used from the main thread, so the plain sets below need no locking.
 */
@Stable
class ClipState(
    private val clips: ClipsRepository?,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
) {
    /** Full names of the repos the person has clipped (of the ones [sync] has looked at). */
    private var clipped by mutableStateOf(emptySet<String>())

    /** Names already sent to the server by [sync], so scrolling never asks twice. */
    private val checked = HashSet<String>()

    /** Names with a clip/unclip request in flight: a second tap on them is ignored. */
    private val busy = HashSet<String>()

    /** False when the app container isn't available (previews, tests): then cards show no clip UI. */
    val enabled: Boolean get() = clips != null

    fun isClipped(fullName: String): Boolean = fullName in clipped

    /** Asks the server which of [items] are clipped, so the cards show the right state. */
    fun sync(items: List<RepoItem>) {
        val repository = clips ?: return
        val fresh = items.map { it.fullName }.filter { checked.add(it) }
        if (fresh.isEmpty()) return
        fresh.chunked(100).forEach { names ->
            scope.launch {
                when (val result = repository.clippedAmong(names)) {
                    is ApiResult.Ok -> {
                        // A repo being flipped right now keeps its on-screen state.
                        val settled = names.filter { it !in busy }.toSet()
                        clipped = (clipped - settled) + result.value.filter { it in settled }
                    }
                    // Forget these so the next sync tries again.
                    is ApiResult.Err -> checked.removeAll(names.toSet())
                }
            }
        }
    }

    /** Tap on the bookmark or on "Clip this repo": flips at once, and flips back if the server says no. */
    fun toggle(item: RepoItem) {
        val repository = clips ?: return
        val name = item.fullName
        if (!busy.add(name)) return
        val wasClipped = name in clipped
        clipped = if (wasClipped) clipped - name else clipped + name
        scope.launch {
            val result = if (wasClipped) repository.remove(name) else repository.add(item)
            if (result is ApiResult.Err) {
                clipped = if (wasClipped) clipped + name else clipped - name
                onMessage(result.message)
            } else if (!wasClipped) {
                onMessage("Clipped. Find it on your profile.")
            }
            busy.remove(name)
        }
    }

    /**
     * Clips [item] into [collection], the one picked in the "Save to collection" sheet. Marks it
     * clipped at once and undoes that if the server says no.
     */
    fun save(item: RepoItem, collection: RepoCollection) {
        val repository = clips ?: return
        val name = item.fullName
        if (!busy.add(name)) return
        val wasClipped = name in clipped
        clipped = clipped + name
        scope.launch {
            val result = repository.add(item, collection.id)
            if (result is ApiResult.Err) {
                clipped = if (wasClipped) clipped + name else clipped - name
                onMessage(result.message)
            } else {
                onMessage("Saved to ${collection.name}")
            }
            busy.remove(name)
        }
    }
}

/** A [ClipState] for the signed-in account: it starts over when the account changes. */
@Composable
fun rememberClipState(userId: Long): ClipState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) {
        (context.applicationContext as? GitHuntApp)?.container?.clipsRepository
    }
    return remember(repository, userId) {
        ClipState(repository, scope) { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
