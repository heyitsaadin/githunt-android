package com.githunt.android.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.discover.DiscoverRepository
import com.githunt.android.data.discover.SortMode
import com.githunt.android.data.engagement.EngagementRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val mode: SortMode = SortMode.Trending,
    /** Empty means all languages. */
    val language: String = "",
    val searchOpen: Boolean = false,
    /** What is typed in the box. */
    val searchInput: String = "",
    /** The search that is actually applied. */
    val search: String = "",
    val items: List<RepoItem> = emptyList(),
    /** First load, nothing to show yet. */
    val loading: Boolean = true,
    /** Pull-to-refresh with items already on screen. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int = 1,
    val error: String? = null,
    /** Full names of the repos the person has clipped. */
    val clipped: Set<String> = emptySet(),
    /** A one-off line for a toast. */
    val message: String? = null,
)

class DiscoverViewModel(
    private val repo: DiscoverRepository,
    private val clips: ClipsRepository,
    private val engagement: EngagementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var started = false
    private var boundUser: Long? = null

    init {
        // "Not interested" on a card takes it off the screen straight away.
        viewModelScope.launch {
            engagement.hidden.collect { hidden ->
                if (hidden.isNotEmpty()) {
                    _state.update { st -> st.copy(items = st.items.filter { item -> item.fullName !in hidden }) }
                }
            }
        }
    }

    /** Discover hits GitHub through the server, so it only loads once the tab is first opened. */
    fun ensureLoaded() {
        if (started) return
        started = true
        reload(refresh = false)
    }

    /** Clips belong to a person: when the signed-in account changes, forget the old ones. */
    fun bind(userId: Long) {
        if (boundUser == userId) return
        boundUser = userId
        _state.update { it.copy(clipped = emptySet()) }
        syncClips(_state.value.items)
    }

    fun refresh() = reload(refresh = true)

    fun setMode(mode: SortMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        reload(refresh = false)
    }

    fun setLanguage(language: String) {
        if (_state.value.language == language) return
        _state.update { it.copy(language = language) }
        reload(refresh = false)
    }

    fun toggleSearch() {
        val current = _state.value
        if (current.searchOpen) {
            // Closing search drops it and goes back to the normal browse feed.
            val hadSearch = current.search.isNotEmpty()
            _state.update { it.copy(searchOpen = false, searchInput = "", search = "") }
            if (hadSearch) reload(refresh = false)
        } else {
            _state.update { it.copy(searchOpen = true) }
        }
    }

    fun onSearchInput(value: String) {
        _state.update { it.copy(searchInput = value.take(200)) }
    }

    fun submitSearch() {
        val query = _state.value.searchInput.trim()
        if (query == _state.value.search) return
        _state.update { it.copy(search = query) }
        reload(refresh = false)
    }

    /** The little x in the search box. */
    fun clearSearchField() {
        val hadSearch = _state.value.search.isNotEmpty()
        _state.update { it.copy(searchInput = "", search = "") }
        if (hadSearch) reload(refresh = false)
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun reload(refresh: Boolean) {
        job?.cancel()
        val keep = refresh && _state.value.items.isNotEmpty()
        _state.update {
            it.copy(
                items = if (keep) it.items else emptyList(),
                loading = !keep,
                refreshing = keep,
                loadingMore = false,
                hasMore = false,
                error = null,
                nextPage = 1,
            )
        }
        val snapshot = _state.value
        job = viewModelScope.launch {
            when (val result = repo.load(snapshot.mode, snapshot.language, snapshot.search, page = 1, force = refresh)) {
                is ApiResult.Ok -> {
                    val hidden = engagement.hidden.value
                    val items = result.value.items
                        .distinctBy { item -> item.fullName }
                        .filter { item -> item.fullName !in hidden }
                    _state.update {
                        it.copy(
                            items = items,
                            hasMore = result.value.hasMore,
                            nextPage = result.value.nextPage,
                            loading = false,
                            refreshing = false,
                        )
                    }
                    syncClips(items)
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
            when (val result = repo.load(current.mode, current.language, current.search, page = current.nextPage, force = false)) {
                is ApiResult.Ok -> {
                    var added: List<RepoItem> = emptyList()
                    _state.update { old ->
                        val seen = old.items.mapTo(HashSet()) { item -> item.fullName }
                        val hidden = engagement.hidden.value
                        added = result.value.items.filter { item ->
                            item.fullName !in seen && item.fullName !in hidden
                        }
                        old.copy(
                            items = old.items + added,
                            hasMore = result.value.hasMore,
                            nextPage = result.value.nextPage,
                            loadingMore = false,
                        )
                    }
                    syncClips(added)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(loadingMore = false, error = result.message)
                }
            }
        }
    }

    /** Tap on the bookmark: flips at once, and flips back if the server says no. */
    fun toggleClip(item: RepoItem) {
        val name = item.fullName
        val wasClipped = name in _state.value.clipped
        _state.update {
            it.copy(clipped = if (wasClipped) it.clipped - name else it.clipped + name)
        }
        viewModelScope.launch {
            val result = if (wasClipped) clips.remove(name) else clips.add(item)
            if (result is ApiResult.Err) {
                _state.update {
                    it.copy(
                        clipped = if (wasClipped) it.clipped + name else it.clipped - name,
                        message = result.message,
                    )
                }
            } else if (!wasClipped) {
                _state.update { it.copy(message = "Clipped. Find it on your profile.") }
            }
        }
    }

    /** Asks the server which of these repos are clipped, so the bookmarks show the right state. */
    private fun syncClips(items: List<RepoItem>) {
        if (boundUser == null || items.isEmpty()) return
        val names = items.map { it.fullName }
        viewModelScope.launch {
            val result = clips.clippedAmong(names)
            if (result is ApiResult.Ok) {
                _state.update { st -> st.copy(clipped = (st.clipped - names.toSet()) + result.value) }
            }
        }
    }
}

class DiscoverViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DiscoverViewModel(
            container.discoverRepository,
            container.clipsRepository,
            container.engagementRepository,
        ) as T
}
