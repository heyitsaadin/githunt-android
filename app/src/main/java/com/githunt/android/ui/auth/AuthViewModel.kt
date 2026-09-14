package com.githunt.android.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.model.User
import com.githunt.android.data.repo.ApiResult
import com.githunt.android.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AuthRepository.getInstance(application)

    val currentUser: StateFlow<User?> = repo.currentUser
    val sessionLoading: StateFlow<Boolean> = repo.isLoading

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        viewModelScope.launch { repo.refreshSession() }
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Enter a username and password.")
            return
        }
        _uiState.value = AuthUiState(isSubmitting = true)
        viewModelScope.launch {
            when (val result = repo.login(username.trim(), password)) {
                is ApiResult.Success -> {
                    _uiState.value = AuthUiState()
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    _uiState.value = AuthUiState(errorMessage = result.message)
                }
            }
        }
    }

    fun signup(username: String, password: String, email: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Fill in all fields.")
            return
        }
        _uiState.value = AuthUiState(isSubmitting = true)
        viewModelScope.launch {
            when (val result = repo.signup(username.trim(), password, email.trim())) {
                is ApiResult.Success -> {
                    _uiState.value = AuthUiState()
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    _uiState.value = AuthUiState(errorMessage = result.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    /** Called after the OAuth WebView bridge copies the session cookie into
     *  PersistentCookieJar — re-fetches /api/auth/me so currentUser reflects
     *  the newly signed-in (or newly GitHub-linked) account. */
    fun refreshAfterOAuth() {
        viewModelScope.launch { repo.refreshSession() }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
