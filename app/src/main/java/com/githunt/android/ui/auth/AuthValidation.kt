package com.githunt.android.ui.auth

import kotlin.random.Random

enum class AuthMode { LogIn, SignUp }

/** Mirrors the server's own rules (see app/api/auth/signup/route.js) so mistakes show up before a round trip. */
object AuthValidation {

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** A message to show, or null when the form is fine to submit. */
    fun check(mode: AuthMode, firstName: String, email: String, password: String): String? =
        when (mode) {
            AuthMode.LogIn ->
                if (email.isBlank() || password.isEmpty()) "Enter your email and password." else null
            AuthMode.SignUp -> when {
                firstName.isBlank() -> "Enter your first name."
                !emailPattern.matches(email.trim()) -> "Enter a valid email address."
                password.length < 6 -> "Password must be at least 6 characters."
                else -> null
            }
        }

    /**
     * The backend still wants a username (3-20 chars: a-z, 0-9, underscore). The sign-up form
     * has no such field, so one is made from the first name plus a random number.
     */
    fun usernameFor(firstName: String): String {
        val base = firstName.lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .take(12)
            .ifEmpty { "user" }
        return "${base}_${Random.nextInt(1000, 10000)}"
    }
}
