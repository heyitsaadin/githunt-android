package com.githunt.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.engagement.EngagementRepository
import com.githunt.android.data.engagement.FeedRanking
import com.githunt.android.data.feed.FeedItem
import com.githunt.android.data.feed.FeedRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    /** First load, nothing to show yet. */
    val loading: Boolean = true,
    /** Pull-to-refresh with items already on screen. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** The next page number to ask the server for. */
    val cursor: Long? = null,
    val error: String? = null,
)

class FeedViewModel(
    private val repo: FeedRepository,
    private val engagement: EngagementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        // "Not interested" on a card takes it off the screen straight away.
        viewModelScope.launch {
            engagement.hidden.collect { hidden ->
                _state.update { it.copy(items = it.items.without(hidden)) }
            }
        }
        refresh()
    }

    /** Top of the feed. Replaces what is on screen. */
    fun refresh() {
        job?.cancel()
        val hadItems = _state.value.items.isNotEmpty()
        _state.update {
            it.copy(loading = !hadItems, refreshing = hadItems, loadingMore = false, error = null)
        }
        job = viewModelScope.launch {
            // The taste profile loads alongside the page so the very first screen is already ranked by it.
            val profile = async { engagement.interestScores(forceRefresh = true) }
            val result = repo.load(page = 0)
            val scores = profile.await()
            when (result) {
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        items = FeedRanking.rank(result.value.items, scores).without(engagement.hidden.value),
                        cursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                        loading = false,
                        refreshing = false,
                    )
                }
                is ApiResult.Err -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    /** Next page, appended below. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loading || current.refreshing || current.loadingMore) return

        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            // Cached unless the person did something since the last page, so the feed keeps learning as they scroll.
            val profile = async { engagement.interestScores() }
            val result = repo.load(page = (current.cursor ?: 1L).toInt())
            val scores = profile.await()
            when (result) {
                is ApiResult.Ok -> _state.update { old ->
                    val seen = old.items.mapTo(HashSet()) { item -> item.key }
                    val fresh = result.value.items.filter { item -> item.key !in seen }
                    old.copy(
                        items = old.items + FeedRanking.rank(fresh, scores).without(engagement.hidden.value),
                        cursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
                }
                is ApiResult.Err -> _state.update {
                    it.copy(loadingMore = false, error = result.message)
                }
            }
        }
    }
}

private fun List<FeedItem>.without(hidden: Set<String>): List<FeedItem> =
    if (hidden.isEmpty()) this else filter { it !is RepoItem || it.fullName !in hidden }

class FeedViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FeedViewModel(container.feedRepository, container.engagementRepository) as T
}
