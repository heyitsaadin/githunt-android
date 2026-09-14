package com.githunt.android.data.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * GitHunt's web app authenticates with a signed, httpOnly session cookie
 * (`pb_session`, see lib/auth.js) set by the login/signup API routes. A
 * browser stores and resends that automatically; OkHttp does not unless you
 * give it a CookieJar. This is the direct native equivalent of "staying
 * logged in" the way the website does, backed by SharedPreferences so the
 * session survives app restarts (same lifetime as the cookie's own 30-day
 * maxAge set server-side).
 *
 * Thread-safety: OkHttp calls saveFromResponse/loadForRequest from its own
 * dispatcher threads, so access to the backing map is synchronized.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("githunt_cookies", Context.MODE_PRIVATE)

    private val lock = Any()

    // host -> (cookie name -> serialized cookie string)
    private val store: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    init {
        synchronized(lock) {
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    val host = key.substringBefore('|')
                    val name = key.substringAfter('|')
                    store.getOrPut(host) { mutableMapOf() }[name] = value
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val hostMap = store.getOrPut(url.host) { mutableMapOf() }
            val editor = prefs.edit()
            for (cookie in cookies) {
                val serialized = cookie.toString()
                hostMap[cookie.name] = serialized
                editor.putString("${url.host}|${cookie.name}", serialized)
            }
            editor.apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val hostMap = store[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            val valid = mutableListOf<Cookie>()
            val expired = mutableListOf<String>()
            for ((name, serialized) in hostMap) {
                val cookie = Cookie.parse(url, serialized)
                if (cookie == null) {
                    expired.add(name)
                    continue
                }
                if (cookie.expiresAt < now) {
                    expired.add(name)
                } else {
                    valid.add(cookie)
                }
            }
            if (expired.isNotEmpty()) {
                val editor = prefs.edit()
                expired.forEach {
                    hostMap.remove(it)
                    editor.remove("${url.host}|$it")
                }
                editor.apply()
            }
            return valid
        }
    }

    /** Clears every stored cookie — used on logout / delete-account. */
    fun clear() {
        synchronized(lock) {
            store.clear()
            prefs.edit().clear().apply()
        }
    }
}
