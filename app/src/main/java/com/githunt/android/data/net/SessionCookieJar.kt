package com.githunt.android.data.net

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Serializable
internal data class StoredCookie(
    val name: String,
    val value: String,
    val host: String,
    val expiresAt: Long,
)

/**
 * Keeps the backend's httpOnly session cookie (`gh_session`) across app
 * restarts, the same way a browser stays signed in. Password login, signup
 * and Google sign-in all answer with that one cookie, so every sign-in method
 * ends up here and the rest of the app never cares which one was used.
 *
 * Logout (and a revoked session) arrives as an expired cookie, which removes
 * the stored copy.
 */
class SessionCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val codec = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StoredCookie.serializer())
    private val lock = Any()
    private val store = mutableListOf<StoredCookie>()

    init {
        synchronized(lock) {
            val raw = prefs.getString(KEY, null)
            if (raw != null) {
                try {
                    store.addAll(codec.decodeFromString(listSerializer, raw))
                } catch (e: Exception) {
                    // Corrupt or old format: start signed out rather than crash.
                    prefs.edit().remove(KEY).apply()
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                store.removeAll { it.name == cookie.name && it.host == url.host }
                if (cookie.value.isNotEmpty() && cookie.expiresAt > now) {
                    store.add(StoredCookie(cookie.name, cookie.value, url.host, cookie.expiresAt))
                }
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (store.removeAll { it.expiresAt <= now }) persist()
        store.filter { it.host == url.host }.map { it.toCookie() }
    }

    /** True when a non-expired cookie is stored. Says nothing about whether the server still accepts it. */
    fun hasSession(): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        store.any { it.expiresAt > now }
    }

    fun clear() {
        synchronized(lock) {
            store.clear()
            persist()
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY, codec.encodeToString(listSerializer, store.toList())).apply()
    }

    private fun StoredCookie.toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .hostOnlyDomain(host)
        .path("/")
        .expiresAt(expiresAt)
        .httpOnly()
        .secure()
        .build()

    private companion object {
        const val PREFS = "githunt_session"
        const val KEY = "cookies"
    }
}
