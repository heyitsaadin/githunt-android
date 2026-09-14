package com.githunt.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the shape returned by GET/PATCH /api/auth/me and the `user` object
 * from /api/auth/login and /api/auth/signup. Nullable fields match nullable
 * Postgres columns in schema.sql.
 */
@Serializable
data class User(
    val id: Int,
    val username: String,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val background_url: String? = null,
    val email: String? = null,
    val email_verified: Boolean = false,
    val github_username: String? = null,
    val github_id: String? = null,
    val has_google: Boolean = false,
    val has_password: Boolean = true,
    val follower_count: Int? = null,
    val following_count: Int? = null,
)

@Serializable
data class MeResponse(val user: User? = null)

@Serializable
data class AuthResponse(val user: User? = null, val error: String? = null, val code: String? = null)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class SignupRequest(val username: String, val password: String, val email: String)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val background_url: String? = null,
    val github_username: String? = null,
)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class ApiError(val error: String? = null, val code: String? = null)
