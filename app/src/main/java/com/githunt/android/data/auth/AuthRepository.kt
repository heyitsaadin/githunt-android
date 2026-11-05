package com.githunt.android.data.auth

import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.SessionCookieJar
import com.githunt.android.data.net.apiCall
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/** What we learned when the app opened and asked "am I still signed in?" */
sealed interface SessionCheck {
    data class SignedIn(val user: User) : SessionCheck
    data object SignedOut : SessionCheck

    /** The server could not answer (cold start, no network). The person is NOT signed out. */
    data class Unavailable(val message: String) : SessionCheck
}

class AuthRepository(
    private val api: AuthApi,
    private val cookieJar: SessionCookieJar,
    private val json: Json,
) {

    suspend fun login(username: String, password: String): ApiResult<User> =
        apiCall(json) { api.login(LoginRequest(username.trim().lowercase(), password)) }.toUser()

    suspend fun signup(username: String, email: String, password: String): ApiResult<User> =
        apiCall(json) {
            api.signup(SignupRequest(username.trim().lowercase(), password, email.trim().lowercase()))
        }.toUser()

    suspend fun signInWithGoogle(idToken: String): ApiResult<User> =
        apiCall(json) { api.googleSignIn(GoogleTokenRequest(idToken)) }.toUser()

    /**
     * Called once at launch. No stored cookie means signed out with no network
     * call at all. A 503 or a network error is retried, and if it still fails we
     * report [SessionCheck.Unavailable] instead of logging the person out (the
     * server's /me route answers 503 on purpose for transient failures).
     */
    suspend fun restoreSession(): SessionCheck {
        if (!cookieJar.hasSession()) return SessionCheck.SignedOut

        var lastMessage = "Can't reach GitHunt right now."
        for (attempt in 1..3) {
            when (val result = apiCall(json) { api.me() }) {
                is ApiResult.Ok -> {
                    val user = result.value.user
                    if (user != null) return SessionCheck.SignedIn(user)
                    cookieJar.clear()
                    return SessionCheck.SignedOut
                }
                is ApiResult.Err -> {
                    if (result.kind == ApiResult.Kind.Unauthorized) {
                        cookieJar.clear()
                        return SessionCheck.SignedOut
                    }
                    lastMessage = result.message
                }
            }
            if (attempt < 3) delay(1500)
        }
        return SessionCheck.Unavailable(lastMessage)
    }

    /** Re-reads the account (for example to see that the email got confirmed). Ok(null) means signed out. */
    suspend fun refreshUser(): ApiResult<User?> =
        when (val result = apiCall(json) { api.me() }) {
            is ApiResult.Ok -> {
                val user = result.value.user
                if (user == null) cookieJar.clear()
                ApiResult.Ok(user)
            }
            is ApiResult.Err -> {
                if (result.kind == ApiResult.Kind.Unauthorized) cookieJar.clear()
                result
            }
        }

    suspend fun resendVerification(): ApiResult<ResendResponse> =
        apiCall(json) { api.resendVerification() }

    /** Revokes the session on the server, then forgets the cookie even if that call failed. */
    suspend fun logout() {
        try {
            apiCall(json) { api.logout() }
        } finally {
            cookieJar.clear()
        }
    }

    /** Used when the server tells us the session is gone. */
    fun forgetSession() = cookieJar.clear()
}

private fun ApiResult<UserEnvelope>.toUser(): ApiResult<User> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(this.value.user)
    is ApiResult.Err -> this
}
