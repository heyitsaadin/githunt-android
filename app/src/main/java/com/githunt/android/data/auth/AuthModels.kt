package com.githunt.android.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The signed-in account, as returned by /api/auth/login, /signup, /google/mobile and /me. */
@Serializable
data class User(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("background_url") val backgroundUrl: String? = null,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    @SerialName("github_username") val githubUsername: String? = null,
    @SerialName("has_google") val hasGoogle: Boolean = false,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
)

@Serializable
data class UserEnvelope(
    val user: User,
    @SerialName("new_account") val newAccount: Boolean = false,
)

/** /api/auth/me answers `{ user: null }` when signed out. */
@Serializable
data class MeEnvelope(
    val user: User? = null,
    val error: String? = null,
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class SignupRequest(val username: String, val password: String, val email: String)

@Serializable
data class GoogleTokenRequest(val idToken: String)

/** /api/auth/verify-email with `Accept: application/json`. Failures come back as `{ ok: false, error }`. */
@Serializable
data class VerifyEmailResponse(
    val ok: Boolean = false,
    val username: String? = null,
)

@Serializable
data class ResendResponse(
    val ok: Boolean = false,
    val sent: Boolean? = null,
    val alreadyVerified: Boolean = false,
)
