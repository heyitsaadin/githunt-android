package com.githunt.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.discover.DiscoverRepository
import com.githunt.android.data.discover.SortMode
import com.githunt.android.data.engagement.EngagementEvent
import com.githunt.android.data.engagement.EngagementTracker
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The languages offered on the first setup step (the Discover list plus a few more). */
val OnboardingLanguages: List<String> = listOf(
    "JavaScript", "TypeScript", "Python", "Java", "Go", "Rust", "C++", "C", "C#",
    "PHP", "Ruby", "Swift", "Kotlin", "Dart", "Scala", "Lua", "Zig", "Elixir",
    "Haskell", "Vue", "HTML", "CSS", "Shell",
)

const val MAX_ONBOARDING_LANGUAGES = 5

private const val SUGGESTION_TARGET = 24

enum class OnboardingStep { Languages, Repos }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Languages,
    val languages: Set<String> = emptySet(),
    val repos: List<RepoItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Full names of the suggested repos the person tapped. */
    val picked: Set<String> = emptySet(),
    val saving: Boolean = false,
)

/**
 * First-run setup, shown once after sign-in: pick favourite languages, then tap the trending repos
 * in those languages that look interesting. The picks are sent as engagement events, which is what
 * the server builds the person's taste profile (and so their feed ranking) from.
 */
class OnboardingViewModel(
    private val discover: DiscoverRepository,
    private val tracker: EngagementTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun toggleLanguage(name: String) {
        _state.update { s ->
            when {
                name in s.languages -> s.copy(languages = s.languages - name)
                s.languages.size >= MAX_ONBOARDING_LANGUAGES -> s
                else -> s.copy(languages = s.languages + name)
            }
        }
    }

    fun continueToRepos() {
        if (_state.value.languages.isEmpty()) return
        _state.update { it.copy(step = OnboardingStep.Repos) }
        loadRepos()
    }

    fun retry() = loadRepos()

    fun back() {
        if (_state.value.saving) return
        loadJob?.cancel()
        _state.update { it.copy(step = OnboardingStep.Languages, loading = false, error = null) }
    }

    fun togglePick(item: RepoItem) {
        _state.update { s ->
            val name = item.fullName
            s.copy(picked = if (name in s.picked) s.picked - name else s.picked + name)
        }
    }

    /** Trending repos for each chosen language, mixed so every language shows up near the top. */
    private fun loadRepos() {
        val langs = _state.value.languages.toList()
        if (langs.isEmpty()) return
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null, repos = emptyList()) }
        loadJob = viewModelScope.launch {
            val results = coroutineScope {
                langs.map { lang ->
                    async { discover.load(SortMode.Trending, lang, "", 1, false) }
                }.awaitAll()
            }

            val lists = ArrayList<List<RepoItem>>()
            var firstError: String? = null
            for (result in results) {
                when (result) {
                    is ApiResult.Ok -> if (result.value.items.isNotEmpty()) lists.add(result.value.items)
                    is ApiResult.Err -> if (firstError == null) firstError = result.message
                }
            }

            val perLanguage = (SUGGESTION_TARGET / langs.size).coerceIn(4, 10)
            val mixed = ArrayList<RepoItem>()
            for (i in 0 until perLanguage) {
                for (list in lists) {
                    val item = list.getOrNull(i)
                    if (item != null) mixed.add(item)
                }
            }
            val repos = mixed.distinctBy { it.fullName }

            _state.update {
                it.copy(
                    repos = repos,
                    loading = false,
                    error = if (repos.isEmpty()) (firstError ?: "No repos found for those languages.") else null,
                )
            }
        }
    }

    /**
     * Saves the picks. Each tapped repo counts as a clip (a strong "I like this" signal). A language
     * that was chosen but has no tapped repo still gets one mild signal, so choosing it on the first
     * step counts even if nothing in it was tapped.
     */
    fun finish(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val chosen = s.repos.filter { it.fullName in s.picked }
            val events = ArrayList<Pair<RepoItem, String>>()
            chosen.forEach { events.add(it to EngagementEvent.CLIP) }
            val covered = chosen.mapNotNull { it.language?.lowercase() }.toSet()
            for (lang in s.languages) {
                if (lang.lowercase() in covered) continue
                val sample = s.repos.firstOrNull { it.language.equals(lang, ignoreCase = true) }
                if (sample != null) events.add(sample to EngagementEvent.README)
            }
            if (events.isNotEmpty()) withTimeoutOrNull(6_000) { tracker.recordAll(events) }
            _state.value = OnboardingUiState()
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        if (_state.value.saving) return
        loadJob?.cancel()
        _state.value = OnboardingUiState()
        onDone()
    }
}

class OnboardingViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OnboardingViewModel(container.discoverRepository, container.engagementTracker) as T
}
