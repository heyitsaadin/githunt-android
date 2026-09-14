package com.githunt.android.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.model.EngagementEventRequest
import com.githunt.android.data.model.Repo
import com.githunt.android.data.repo.ApiResult
import com.githunt.android.data.repo.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val repos: List<Repo> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Drives the Discover tab: paginates through GET /api/feed/default exactly
 * the way the web app's infinite scroll does (same `page`/`limit` params),
 * and fires engagement events the same way components/RepoCard.js does
 * (dwell-time ≥4s, expand README, share, clip, "not interested", etc.) so
 * the same per-account interest-scoring model on the backend keeps working
 * unchanged for native clients too.
 */
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FeedRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState

    init {
        loadNextPage()
    }

    fun refresh() {
        _uiState.value = DiscoverUiState(isRefreshing = true)
        loadNextPage(resetting = true)
    }

    fun loadNextPage(resetting: Boolean = false) {
        val state = _uiState.value
        if (state.isLoadingMore || (!state.hasMore && !resetting)) return

        _uiState.value = state.copy(isLoadingMore = true, errorMessage = null)
        viewModelScope.launch {
            val pageToLoad = if (resetting) 0 else state.page
            when (val result = repo.loadDiscoverPage(pageToLoad)) {
                is ApiResult.Success -> {
                    val body = result.data
                    val existing = if (resetting) emptyList() else _uiState.value.repos
                    _uiState.value = DiscoverUiState(
                        repos = existing + body.repos,
                        page = pageToLoad + 1,
                        hasMore = body.has_more,
                        isLoadingMore = false,
                        isRefreshing = false,
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun recordEngagement(repoFullName: String, eventType: String, language: String?, topics: List<String>) {
        viewModelScope.launch {
            repo.recordEngagement(EngagementEventRequest(repoFullName, eventType, language, topics))
        }
    }

    /** "Not interested" also removes the repo from the visible list immediately. */
    fun markNotInterested(target: Repo) {
        recordEngagement(target.full_name, "not_interested", target.language, target.topics)
        _uiState.value = _uiState.value.copy(repos = _uiState.value.repos.filterNot { it.full_name == target.full_name })
    }
}
