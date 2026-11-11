package com.githunt.android.data.translate

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One translated string. [same] is true when the text was already in the target language. */
internal class Translated(val text: String, val same: Boolean)

/**
 * Calls Google's public "gtx" translate endpoint (the one translate.google.com's widgets use)
 * straight from the phone, the same request the website's /api/translate makes from its server.
 * Going direct skips the hop through Vercel and uses this phone's own IP address, which Google
 * blocks far less often than a datacenter's.
 *
 * It is unofficial and has no SLA. After a 429, a 403 or a captcha page it steps aside for a
 * minute, so callers fall back instead of hammering it.
 */
internal class GoogleTranslateClient(client: OkHttpClient? = null) {

    // Its own client: nothing from the GitHunt session (cookies, headers) may reach Google.
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var blockedUntil = 0L

    /** Translates one string, or returns null when Google can't be used right now. */
    suspend fun translate(text: String, target: String): Translated? {
        if (SystemClock.elapsedRealtime() < blockedUntil) return null
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        if (url.toString().length > MAX_URL_CHARS) return null
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).await().use { response -> read(response, text, target) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun read(response: Response, text: String, target: String): Translated? {
        if (response.code == 429 || response.code == 403) {
            stepAside()
            return null
        }
        if (!response.isSuccessful) return null
        // Google answers with an HTML captcha page (still 200 OK) when it rate-limits an address.
        if (!response.header("Content-Type").orEmpty().contains("json")) {
            stepAside()
            return null
        }
        val raw = response.body?.string() ?: return null
        return parse(raw, text, target)
    }

    private fun parse(raw: String, original: String, target: String): Translated? = try {
        val root = json.parseToJsonElement(raw).jsonArray
        // With sl=auto Google also reports the language it detected (index 2). When that is already
        // the target, hand the text back untouched instead of a reworded copy.
        val detected = root.getOrNull(2)?.jsonPrimitive?.contentOrNull
        if (detected != null && baseLanguage(detected) == baseLanguage(target)) {
            Translated(original, same = true)
        } else {
            val out = StringBuilder()
            root.getOrNull(0)?.jsonArray?.forEach { chunk ->
                chunk.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull?.let { out.append(it) }
            }
            val result = out.toString()
            if (result.isBlank() && original.isNotBlank()) null else Translated(result, same = false)
        }
    } catch (e: Exception) {
        null
    }

    private fun stepAside() {
        blockedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
    }

    private fun baseLanguage(code: String) = code.lowercase().substringBefore('-')

    private companion object {
        const val BASE = "https://translate.googleapis.com/translate_a/single"
        const val MAX_URL_CHARS = 8_000
        const val COOLDOWN_MS = 60_000L

        // A default client sends no browser-like User-Agent, one of the signals Google uses to
        // rate-limit obvious non-browser traffic harder.
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36"
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
