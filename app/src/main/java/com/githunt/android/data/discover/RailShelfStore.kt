package com.githunt.android.data.discover

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The themes the website's RandomRepoRail picks from. [topic] is the GitHub topic it searches. */
data class RailTheme(val label: String, val topic: String)

val RailThemes = listOf(
    RailTheme("AI & Agents", "ai-agents"),
    RailTheme("CLI Tools", "cli"),
    RailTheme("Game Dev", "game-development"),
    RailTheme("Self-Hosted", "selfhosted"),
    RailTheme("Dev Tools", "developer-tools"),
    RailTheme("Web Frameworks", "framework"),
    RailTheme("Data & ML", "machine-learning"),
    RailTheme("Terminal & Shell", "terminal"),
    RailTheme("Open Source Fun", "awesome"),
)

sealed interface RailShelf {
    data class Loading(val label: String) : RailShelf
    data class Ready(val label: String, val repos: List<RepoItem>) : RailShelf

    /** Nothing usable came back (no repos with a README image, or the request failed). */
    object Empty : RailShelf
}

/**
 * Loads and remembers the feed's side-scrolling rails, one "shelf" per slot, so a rail that scrolls
 * off screen and back doesn't refetch or change. Mirrors the website's RandomRepoRail:
 *
 *  - each slot gets one of [RailThemes]; the order is shuffled so neighbouring rails differ,
 *  - it asks for `mode=top` on a random page (1 or 2) of the theme's topic,
 *  - only repos that have a README image are kept; if fewer than [MIN_CARDS] qualify, the other
 *    page is fetched once to top it up,
 *  - at most [MAX_CARDS] repos per rail.
 *
 * Everything here runs on the main thread; the network call suspends off it.
 */
class RailShelfStore(private val discover: DiscoverRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _shelves = MutableStateFlow<Map<Int, RailShelf>>(emptyMap())
    val shelves: StateFlow<Map<Int, RailShelf>> = _shelves.asStateFlow()

    private var order = RailThemes.shuffled()
    private var generation = 0
    private val requested = HashSet<Int>()

    /** Starts loading [slot] once. Safe to call on every composition. */
    fun ensure(slot: Int) {
        if (!requested.add(slot)) return
        val theme = order[slot % order.size]
        val gen = generation
        _shelves.update { it + (slot to RailShelf.Loading(theme.label)) }
        scope.launch {
            val repos = fetch(theme.topic)
            if (gen != generation) return@launch
            val shelf = if (repos.isEmpty()) RailShelf.Empty else RailShelf.Ready(theme.label, repos)
            _shelves.update { it + (slot to shelf) }
        }
    }

    /** Pull-to-refresh: forget every shelf and pick a fresh theme order. */
    fun reset() {
        generation++
        requested.clear()
        order = RailThemes.shuffled()
        _shelves.value = emptyMap()
    }

    private suspend fun fetch(topic: String): List<RepoItem> {
        val firstPage = 1 + Random.nextInt(2)
        var found = page(topic, firstPage).filter { it.readmeImages.isNotEmpty() }
        if (found.size < MIN_CARDS) {
            val other = if (firstPage == 1) 2 else 1
            val seen = found.mapTo(HashSet()) { it.fullName }
            found = found + page(topic, other).filter { it.readmeImages.isNotEmpty() && it.fullName !in seen }
        }
        return found.take(MAX_CARDS)
    }

    private suspend fun page(topic: String, page: Int): List<RepoItem> =
        when (val result = discover.loadTopic(topic, page)) {
            is ApiResult.Ok -> result.value
            is ApiResult.Err -> emptyList()
        }

    private companion object {
        const val MAX_CARDS = 10
        const val MIN_CARDS = 4
    }
}
