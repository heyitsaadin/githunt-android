package com.githunt.android.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.model.AiChatTurn
import com.githunt.android.data.model.Repo
import com.githunt.android.data.model.RepoAiContext
import com.githunt.android.data.repo.ApiResult
import com.githunt.android.data.repo.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AskAiUiState(
    val messages: List<AiChatTurn> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Mirrors the web app's per-card Ask AI chat: no server-side conversation
 * storage — the client resends recent history each turn (see
 * app/api/ai-chat/route.js's comment on this) — so state lives here only,
 * scoped to one open panel.
 */
class AskAiViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FeedRepository.getInstance(application)

    private val _uiState = MutableStateFlow(AskAiUiState())
    val uiState: StateFlow<AskAiUiState> = _uiState

    fun send(question: String, targetRepo: Repo) {
        if (question.isBlank()) return
        val userTurn = AiChatTurn("user", question.trim())
        val historyForRequest = _uiState.value.messages
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userTurn,
            isSending = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            val context = RepoAiContext(
                full_name = targetRepo.full_name,
                description = targetRepo.description,
                language = targetRepo.language,
                topics = targetRepo.topics,
                stars = targetRepo.stargazers_count,
                forks = targetRepo.forks_count,
                html_url = targetRepo.html_url,
                readme_snippet = targetRepo.readme_snippet,
            )
            when (val result = repo.askAi(question.trim(), context, historyForRequest)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + AiChatTurn("assistant", result.data),
                        isSending = false,
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
                }
            }
        }
    }
}
