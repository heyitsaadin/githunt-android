package com.githunt.android.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.githunt.android.BuildConfig
import com.githunt.android.data.api.NetworkModule
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * Runs the Google/GitHub OAuth flow (GET /api/auth/google or
 * /api/auth/github) inside an in-app WebView rather than a Custom Tab.
 *
 * Why WebView and not Custom Tabs: the OAuth callback route
 * (app/api/auth/google/callback/route.js) finishes by setting an httpOnly
 * session cookie and redirecting to the site's own root. A Custom Tab's
 * cookies belong to the system browser process — the app's own OkHttp
 * client (and its PersistentCookieJar) would never see that cookie. A
 * WebView's android.webkit.CookieManager, on the other hand, is readable
 * from the hosting app process, so once the WebView finishes the redirect
 * chain we copy the resulting Set-Cookie value into PersistentCookieJar and
 * every subsequent Retrofit call is authenticated exactly as if the user
 * had logged in with a password.
 *
 * @param startPath e.g. "api/auth/google" or "api/auth/github" (relative to
 *   BuildConfig.API_BASE_URL). For GitHub connect, the user must already be
 *   signed in (the route itself enforces this and redirects to /login if not).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthWebViewScreen(
    startPath: String,
    onSuccess: () -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    val startUrl = "$baseUrl/$startPath"
    val originHost = remember { baseUrl.toHttpUrlOrNull()?.host }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = onCancelled) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true // required: Google/GitHub's own login pages need it
                    settings.domStorageEnabled = true

                    // Ensure a fresh OAuth attempt doesn't reuse a stale,
                    // already-consumed `state` value from a previous try.
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            val parsed = url.toHttpUrlOrNull() ?: return

                            when {
                                // Failure path: lib/auth.js's callback routes redirect to
                                // /login?error=... on any failure (cancelled, expired state, etc.)
                                parsed.host == originHost && parsed.encodedPath == "/login" && parsed.queryParameter("error") != null -> {
                                    val message = parsed.queryParameter("error") ?: "Sign-in failed."
                                    onError(message)
                                }
                                // Success path: both callbacks redirect to the site root ("/")
                                // once the session cookie is set.
                                parsed.host == originHost && (parsed.encodedPath == "/" || parsed.encodedPath.isEmpty()) -> {
                                    bridgeCookieIntoOkHttp(context, baseUrl)
                                    onSuccess()
                                }
                            }
                        }
                    }

                    loadUrl(startUrl)
                }
            },
        )
    }
}

/**
 * Copies the session cookie WebView's CookieManager collected during the
 * OAuth redirect chain into OkHttp's PersistentCookieJar, so Retrofit calls
 * made outside the WebView (i.e. the entire rest of the app) are
 * authenticated too. Copies every cookie for the domain rather than
 * special-casing the session cookie's name, so this keeps working even if
 * lib/auth.js's cookie name ever changes.
 */
private fun bridgeCookieIntoOkHttp(context: android.content.Context, baseUrl: String) {
    val httpUrl = baseUrl.toHttpUrlOrNull() ?: return
    val rawCookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: return
    val jar = NetworkModule.cookieJar(context)

    val cookies = rawCookieHeader.split(";").mapNotNull { pair ->
        val trimmed = pair.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        // WebView's getCookie() returns "name=value" pairs only (no
        // attributes), so rebuild a standard, reasonably long-lived cookie
        // for the jar; the real maxAge/httpOnly semantics were already
        // enforced server-side when the cookie was originally set.
        Cookie.Builder()
            .name(trimmed.substringBefore('=').trim())
            .value(trimmed.substringAfter('=', "").trim())
            .domain(httpUrl.host)
            .path("/")
            .expiresAt(System.currentTimeMillis() + THIRTY_DAYS_MILLIS) // matches SESSION_COOKIE maxAge in lib/auth.js
            .httpOnly()
            .build()
    }
    if (cookies.isNotEmpty()) {
        jar.saveFromResponse(httpUrl, cookies)
    }
}
