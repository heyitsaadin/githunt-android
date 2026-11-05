package com.githunt.android.ui.auth

enum class AuthMode { LogIn, SignUp }

/** Mirrors the server's own rules (see app/api/auth/signup/route.js) so mistakes show up before a round trip. */
object AuthValidation {

    private val usernamePattern = Regex("^[a-z0-9_]{3,20}$")
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    const val USERNAME_RULE = "Username must be 3-20 characters: lowercase letters, numbers, underscores."

    /** A message to show, or null when the form is fine to submit. */
    fun check(mode: AuthMode, username: String, email: String, password: String): String? {
        val name = username.trim()
        return when (mode) {
            AuthMode.LogIn ->
                if (name.isEmpty() || password.isEmpty()) "Enter a username and password." else null
            AuthMode.SignUp -> when {
                !usernamePattern.matches(name) -> USERNAME_RULE
                !emailPattern.matches(email.trim()) -> "Enter a valid email address."
                password.length < 6 -> "Password must be at least 6 characters."
                else -> null
            }
        }
    }
}
