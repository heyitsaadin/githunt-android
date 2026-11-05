package com.githunt.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.feed.FeedItem
import com.githunt.android.data.feed.FeedRepository
import com.githunt.android.data.net.ApiResult
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.Job
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
    val cursor: Long? = null,
    val error: String? = null,
)

class FeedViewModel(private val repo: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Newest page. Replaces what is on screen. */
    fun refresh() {
        job?.cancel()
        val hadItems = _state.value.items.isNotEmpty()
        _state.update {
            it.copy(loading = !hadItems, refreshing = hadItems, loadingMore = false, error = null)
        }
        job = viewModelScope.launch {
            when (val result = repo.load(before = null)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        items = result.value.items,
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

    /** Older items, appended below. */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loading || current.refreshing || current.loadingMore) return

        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            when (val result = repo.load(before = current.cursor)) {
                is ApiResult.Ok -> _state.update { old ->
                    val seen = old.items.mapTo(HashSet()) { item -> item.key }
                    old.copy(
                        items = old.items + result.value.items.filter { item -> item.key !in seen },
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

class FeedViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FeedViewModel(container.feedRepository) as T
}
