package com.githunt.android.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.githunt.android.data.auth.AuthRepository
import com.githunt.android.data.auth.GoogleResult
import com.githunt.android.data.auth.GoogleSignIn
import com.githunt.android.data.auth.SessionCheck
import com.githunt.android.data.auth.User
import com.githunt.android.data.net.ApiResult
import com.githunt.android.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val user: User) : Session

    /** The server could not be reached. The person is NOT signed out; offer a retry. */
    data class Unavailable(val message: String) : Session
}

data class AuthFormState(
    val mode: AuthMode = AuthMode.LogIn,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val googleBusy: Boolean = false,
    val error: String? = null,
)

/** State for the signed-in placeholder screen (email confirmation). */
data class HomeState(
    val refreshing: Boolean = false,
    val resending: Boolean = false,
    val notice: String? = null,
)

class AuthViewModel(
    private val repo: AuthRepository,
    private val google: GoogleSignIn,
) : ViewModel() {

    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    init {
        restore()
    }

    /** Launch-time check: are we still signed in? Also the "Retry" button when the server is unreachable. */
    fun restore() {
        _session.value = Session.Loading
        viewModelScope.launch {
            _session.value = when (val check = repo.restoreSession()) {
                is SessionCheck.SignedIn -> Session.SignedIn(check.user)
                SessionCheck.SignedOut -> Session.SignedOut
                is SessionCheck.Unavailable -> Session.Unavailable(check.message)
            }
        }
    }

    // ---- form ----

    fun setMode(mode: AuthMode) {
        _form.update { it.copy(mode = mode, error = null) }
    }

    fun onUsernameChange(value: String) {
        val cleaned = value.lowercase().filter { c -> !c.isWhitespace() }.take(32)
        _form.update { it.copy(username = cleaned, error = null) }
    }

    fun onEmailChange(value: String) {
        val cleaned = value.filter { c -> !c.isWhitespace() }.take(120)
        _form.update { it.copy(email = cleaned, error = null) }
    }

    fun onPasswordChange(value: String) {
        _form.update { it.copy(password = value.take(128), error = null) }
    }

    fun submit() {
        val current = _form.value
        if (current.busy || current.googleBusy) return

        val problem = AuthValidation.check(current.mode, current.username, current.email, current.password)
        if (problem != null) {
            _form.update { it.copy(error = problem) }
            return
        }

        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = when (current.mode) {
                AuthMode.LogIn -> repo.login(current.username, current.password)
                AuthMode.SignUp -> repo.signup(current.username, current.email, current.password)
            }
            handleAuthResult(result)
        }
    }

    /** [activityContext] must be the Activity: Credential Manager draws its account picker on top of it. */
    fun signInWithGoogle(activityContext: Context) {
        val current = _form.value
        if (current.busy || current.googleBusy) return

        if (!google.isConfigured) {
            _form.update { it.copy(error = "Google sign-in isn't set up in this build yet.") }
            return
        }

        _form.update { it.copy(googleBusy = true, error = null) }
        viewModelScope.launch {
            when (val outcome = google.requestIdToken(activityContext)) {
                is GoogleResult.Token -> handleAuthResult(repo.signInWithGoogle(outcome.idToken))
                GoogleResult.Cancelled -> _form.update { it.copy(googleBusy = false) }
                is GoogleResult.Failed -> _form.update { it.copy(googleBusy = false, error = outcome.message) }
            }
        }
    }

    private fun handleAuthResult(result: ApiResult<User>) {
        when (result) {
            is ApiResult.Ok -> {
                _form.value = AuthFormState()
                _home.value = HomeState()
                _session.value = Session.SignedIn(result.value)
            }
            is ApiResult.Err -> _form.update {
                it.copy(busy = false, googleBusy = false, error = result.message)
            }
        }
    }

    // ---- signed in ----

    /** [manual] = the person tapped the button, so tell them when nothing changed. */
    private var refreshJob: Job? = null
    private var signingOut = false

    fun refreshUser(manual: Boolean) {
        if (signingOut) return
        val signedIn = _session.value as? Session.SignedIn ?: return
        if (_home.value.refreshing) return
        // Automatic refreshes (on resume) only matter while the email is unconfirmed.
        if (!manual && signedIn.user.emailVerified) return

        _home.update { it.copy(refreshing = true, notice = if (manual) null else it.notice) }
        refreshJob = viewModelScope.launch {
            try {
                when (val result = repo.refreshUser()) {
                    is ApiResult.Ok -> {
                        val user = result.value
                        if (user == null) {
                            _session.value = Session.SignedOut
                        } else {
                            _session.value = Session.SignedIn(user)
                            if (manual && !user.emailVerified) {
                                _home.update { it.copy(notice = "Not confirmed yet. Open the link in the email first.") }
                            }
                        }
                    }
                    is ApiResult.Err -> {
                        if (result.kind == ApiResult.Kind.Unauthorized) {
                            _session.value = Session.SignedOut
                        } else if (manual) {
                            _home.update { it.copy(notice = result.message) }
                        }
                    }
                }
            } finally {
                _home.update { it.copy(refreshing = false) }
            }
        }
    }

    fun resendVerification() {
        if (_home.value.resending) return

        _home.update { it.copy(resending = true, notice = null) }
        viewModelScope.launch {
            val message = when (val result = repo.resendVerification()) {
                is ApiResult.Ok -> when {
                    result.value.alreadyVerified -> "Your email is already confirmed. Tap the button above."
                    result.value.sent == false -> "The email couldn't be sent right now. Try again in a few minutes."
                    else -> "Sent. Check your inbox and your spam folder."
                }
                is ApiResult.Err -> result.message
            }
            _home.update { it.copy(resending = false, notice = message) }
        }
    }

    fun signOut() {
        if (signingOut) return
        signingOut = true
        refreshJob?.cancel()
        viewModelScope.launch {
            try {
                repo.logout()
            } finally {
                _form.value = AuthFormState()
                _home.value = HomeState()
                _session.value = Session.SignedOut
                signingOut = false
            }
        }
    }
}

class AuthViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(container.authRepository, container.googleSignIn) as T
}
