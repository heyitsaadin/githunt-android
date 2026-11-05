package com.githunt.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.auth.User
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.profile.FollowingItem
import com.githunt.android.data.profile.ProfilePost
import com.githunt.android.data.profile.ProfileRepository
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The website's profile tabs. "Repos" only shows once a GitHub account is connected. */
enum class ProfileTab(val label: String) {
    Posts("Posts"),
    Following("Following"),
    Repos("Repos"),
    Clipped("Clipped"),
}

/** One tab's list: not asked for yet, loading, or loaded (with maybe an error). */
data class Section<T>(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val items: List<T> = emptyList(),
    val error: String? = null,
)

data class ProfileUiState(
    val tab: ProfileTab = ProfileTab.Posts,
    val posts: Section<ProfilePost> = Section(),
    val following: Section<FollowingItem> = Section(),
    val repos: Section<RepoItem> = Section(),
    val clipped: Section<RepoItem> = Section(),
    val refreshing: Boolean = false,
)

class ProfileViewModel(
    private val repo: ProfileRepository,
    private val clips: ClipsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var boundUser: User? = null

    /** Called whenever the profile is shown. Starts fresh when a different person signed in. */
    fun bind(user: User) {
        val previous = boundUser
        boundUser = user
        if (previous == null || previous.id != user.id) {
            _state.value = ProfileUiState()
            viewModelScope.launch { loadNow(ProfileTab.Posts) }
            return
        }
        if (previous.githubUsername != user.githubUsername) {
            _state.update {
                it.copy(
                    repos = Section(),
                    tab = if (it.tab == ProfileTab.Repos && user.githubUsername == null) ProfileTab.Posts else it.tab,
                )
            }
        }
        // Clips change on the Discover tab, so look again when the Clipped tab is what is showing.
        if (_state.value.tab == ProfileTab.Clipped) {
            viewModelScope.launch { loadNow(ProfileTab.Clipped) }
        }
    }

    fun selectTab(tab: ProfileTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == ProfileTab.Clipped || !isLoaded(tab)) {
            viewModelScope.launch { loadNow(tab) }
        }
    }

    /** Pull-to-refresh: reload whichever tab is showing. */
    fun refresh() {
        val tab = _state.value.tab
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            loadNow(tab)
            _state.update { it.copy(refreshing = false) }
        }
    }

    /** Takes a repo off the Clipped tab straight away; puts the list back if the server refuses. */
    fun unclip(item: RepoItem) {
        _state.update {
            it.copy(clipped = it.clipped.copy(items = it.clipped.items.filter { r -> r.fullName != item.fullName }))
        }
        viewModelScope.launch {
            if (clips.remove(item.fullName) is ApiResult.Err) loadNow(ProfileTab.Clipped)
        }
    }

    private fun isLoaded(tab: ProfileTab): Boolean {
        val s = _state.value
        return when (tab) {
            ProfileTab.Posts -> s.posts.loaded || s.posts.loading
            ProfileTab.Following -> s.following.loaded || s.following.loading
            ProfileTab.Repos -> s.repos.loaded || s.repos.loading
            ProfileTab.Clipped -> s.clipped.loaded || s.clipped.loading
        }
    }

    private suspend fun loadNow(tab: ProfileTab) {
        val user = boundUser ?: return
        when (tab) {
            ProfileTab.Posts -> {
                _state.update { it.copy(posts = it.posts.copy(loading = true, error = null)) }
                val result = repo.posts(user.id)
                if (boundUser?.id != user.id) return
                _state.update { it.copy(posts = it.posts.after(result)) }
            }
            ProfileTab.Following -> {
                _state.update { it.copy(following = it.following.copy(loading = true, error = null)) }
                val result = repo.following()
                if (boundUser?.id != user.id) return
                _state.update { it.copy(following = it.following.after(result)) }
            }
            ProfileTab.Repos -> {
                if (user.githubUsername == null) {
                    _state.update { it.copy(repos = Section(loaded = true)) }
                    return
                }
                _state.update { it.copy(repos = it.repos.copy(loading = true, error = null)) }
                val result = repo.repos(user.id)
                if (boundUser?.id != user.id) return
                _state.update { it.copy(repos = it.repos.after(result)) }
            }
            ProfileTab.Clipped -> {
                _state.update { it.copy(clipped = it.clipped.copy(loading = true, error = null)) }
                val result = repo.clipped()
                if (boundUser?.id != user.id) return
                _state.update { it.copy(clipped = it.clipped.after(result)) }
            }
        }
    }
}

/** Keeps what was on screen if a reload fails, and says why. */
private fun <T> Section<T>.after(result: ApiResult<List<T>>): Section<T> = when (result) {
    is ApiResult.Ok -> Section(loaded = true, items = result.value)
    is ApiResult.Err -> copy(loaded = true, loading = false, error = result.message)
}

class ProfileViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileViewModel(container.profileRepository, container.clipsRepository) as T
}
