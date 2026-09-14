package com.githunt.android.data.repo

import android.content.Context
import com.githunt.android.data.api.NetworkModule
import com.githunt.android.data.model.*
import com.githunt.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val code: String? = null, val httpStatus: Int? = null) : ApiResult<Nothing>()
}

/**
 * Holds the current signed-in user (or null for guest) as observable state,
 * and wraps every auth-related API call. Screens observe [currentUser]
 * rather than each making their own /api/auth/me call.
 */
class AuthRepository(private val context: Context) {

    private val api = NetworkModule.api(context)
    private val errorJson = Json { ignoreUnknownKeys = true }

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** Call once at app start — mirrors the web app's AuthProvider mount check. */
    suspend fun refreshSession() {
        _isLoading.value = true
        try {
            val res = api.me()
            if (!res.isSuccessful) {
                AppLog.w("AuthRepository", "refreshSession: HTTP ${res.code()}, body=${res.errorBody()?.string()}")
            }
            _currentUser.value = if (res.isSuccessful) res.body()?.user else null
        } catch (e: Exception) {
            AppLog.e("AuthRepository", "refreshSession threw", e)
            _currentUser.value = null
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun login(username: String, password: String): ApiResult<User> {
        return try {
            val res = api.login(LoginRequest(username, password))
            if (res.isSuccessful && res.body()?.user != null) {
                _currentUser.value = res.body()!!.user
                ApiResult.Success(res.body()!!.user!!)
            } else {
                val errorBody = res.errorBody()?.string()
                AppLog.w("AuthRepository", "login failed: HTTP ${res.code()}, body=$errorBody")
                errorFrom(errorBody, res.code())
            }
        } catch (e: Exception) {
            AppLog.e("AuthRepository", "login threw", e)
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun signup(username: String, password: String, email: String): ApiResult<User> {
        return try {
            val res = api.signup(SignupRequest(username, password, email))
            if (res.isSuccessful && res.body()?.user != null) {
                _currentUser.value = res.body()!!.user
                ApiResult.Success(res.body()!!.user!!)
            } else {
                val errorBody = res.errorBody()?.string()
                AppLog.w("AuthRepository", "signup failed: HTTP ${res.code()}, body=$errorBody")
                errorFrom(errorBody, res.code())
            }
        } catch (e: Exception) {
            AppLog.e("AuthRepository", "signup threw", e)
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
            // Best-effort server-side revoke; clear local state regardless.
        }
        NetworkModule.cookieJar(context).clear()
        _currentUser.value = null
    }

    suspend fun updateProfile(body: UpdateProfileRequest): ApiResult<User> {
        return try {
            val res = api.updateProfile(body)
            if (res.isSuccessful && res.body()?.user != null) {
                _currentUser.value = res.body()!!.user
                ApiResult.Success(res.body()!!.user!!)
            } else {
                errorFrom(res.errorBody()?.string(), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun deleteAccount(): ApiResult<Unit> {
        return try {
            val res = api.deleteAccount()
            if (res.isSuccessful) {
                NetworkModule.cookieJar(context).clear()
                _currentUser.value = null
                ApiResult.Success(Unit)
            } else {
                errorFrom(res.errorBody()?.string(), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> {
        return try {
            val res = api.changePassword(ChangePasswordRequest(current, new))
            if (res.isSuccessful) ApiResult.Success(Unit) else errorFrom(res.errorBody()?.string(), res.code())
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun resendVerification(): ApiResult<Unit> {
        return try {
            val res = api.resendVerification()
            if (res.isSuccessful) ApiResult.Success(Unit) else errorFrom(res.errorBody()?.string(), res.code())
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    private fun errorFrom(body: String?, httpStatus: Int): ApiResult.Failure {
        val parsed = try {
            body?.let { errorJson.decodeFromString<ApiError>(it) }
        } catch (_: Exception) {
            null
        }
        return ApiResult.Failure(
            message = parsed?.error ?: "Something went wrong. Please try again.",
            code = parsed?.code,
            httpStatus = httpStatus,
        )
    }

    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun getInstance(context: Context): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
    }
}
