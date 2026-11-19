package com.githunt.android.data.analytics

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Reports app visits to the same analytics the website feeds, so the admin console can show Android
 * traffic next to website traffic (it tells them apart by the app's User-Agent).
 *
 * Same model as the website's VisitTracker:
 *  - a session starts when the app is opened, and a new one starts after 30 minutes away;
 *  - each screen is a pageview with the time spent on it while the app is on screen;
 *  - while the app is open it reports its time so far every 20 seconds, so a killed app still
 *    keeps roughly the right time.
 *
 * Every call is fire and forget: it never blocks the screen, never throws, and a failed request is
 * dropped. Signed-in people are attributed through the session cookie the app already sends.
 */
class VisitTracker(private val api: AnalyticsApi, context: Context) {

    private class Pageview(val path: String, var hidden: Boolean) {
        var id: JsonPrimitive? = null
        var enteredAt: Long = SystemClock.elapsedRealtime()
        var accumulatedMs: Long = 0
        var closed: Boolean = false
        /** Time to report once the server has answered pageview_enter with an id. */
        var pendingMs: Long? = null
    }

    private val prefs = context.applicationContext.getSharedPreferences("githunt_analytics", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    // All of the below is guarded by `lock`.
    private var sessionKey: String? = null
    // Pageviews wait for this, so the session row is created by session_start (which records the
    // User-Agent) and never by a pageview that arrived first.
    private var sessionStarted: CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private var current: Pageview? = null
    private var lastPath: String? = null
    private var foreground = false
    private var backgroundedAt = 0L
    private var pinger: Job? = null

    /** The app came on screen (Activity started). */
    fun onForeground() {
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            foreground = true
            val expired = sessionKey != null && backgroundedAt != 0L && now - backgroundedAt > SESSION_IDLE_MS
            if (expired) {
                // Away long enough that this counts as a new visit, starting on the screen it left on.
                sessionKey = null
                current = null
                lastPath?.let { startSession(it) }
            } else {
                current?.let {
                    if (it.hidden && !it.closed) {
                        it.enteredAt = now
                        it.hidden = false
                    }
                }
            }
            startPinger()
        }
    }

    /** The app left the screen (Activity stopped). The time on the current screen is reported now. */
    fun onBackground() {
        synchronized(lock) {
            foreground = false
            backgroundedAt = SystemClock.elapsedRealtime()
            pinger?.cancel()
            pinger = null
            val cur = current ?: return@synchronized
            if (cur.hidden || cur.closed) return@synchronized
            cur.accumulatedMs += SystemClock.elapsedRealtime() - cur.enteredAt
            cur.hidden = true
            reportLeave(cur, cur.accumulatedMs)
        }
    }

    /** A screen was opened, e.g. "/app/home". Repeating the current screen does nothing. */
    fun screen(path: String) {
        synchronized(lock) {
            lastPath = path
            val cur = current
            if (cur != null && !cur.closed && cur.path == path) return@synchronized
            if (sessionKey == null) startSession(path) else enterPage(path)
        }
    }

    // ---- internals (call with `lock` held) ----

    private fun startSession(entryPath: String) {
        val key = UUID.randomUUID().toString()
        val (visitor, isNew) = visitorKey()
        val gate = CompletableDeferred<Unit>()
        sessionKey = key
        sessionStarted = gate
        scope.launch {
            try {
                post {
                    put("action", "session_start")
                    put("sessionKey", key)
                    put("visitorKey", visitor)
                    put("entryPath", entryPath)
                    put("isNewVisitor", isNew)
                }
            } finally {
                gate.complete(Unit)
            }
        }
        enterPage(entryPath)
    }

    private fun enterPage(path: String) {
        val key = sessionKey ?: return
        val gate = sessionStarted
        current?.let { prev ->
            if (!prev.closed) {
                prev.closed = true
                reportLeave(prev, activeMs(prev))
            }
        }
        val cur = Pageview(path, hidden = !foreground)
        current = cur
        scope.launch {
            gate.await()
            val id = post {
                put("action", "pageview_enter")
                put("sessionKey", key)
                put("path", path)
            }?.pageviewId ?: return@launch
            val pending = synchronized(lock) {
                cur.id = id
                cur.pendingMs
            }
            // The screen was left (or the app backgrounded) before the id arrived: report it now.
            if (pending != null) sendLeave(id, pending)
        }
    }

    private fun reportLeave(cur: Pageview, ms: Long) {
        val id = cur.id
        if (id == null) {
            cur.pendingMs = ms
            return
        }
        scope.launch { sendLeave(id, ms) }
    }

    private suspend fun sendLeave(id: JsonPrimitive, ms: Long) {
        post {
            put("action", "pageview_leave")
            put("pageviewId", id)
            put("durationMs", ms)
        }
    }

    private fun activeMs(p: Pageview): Long =
        p.accumulatedMs + if (p.hidden) 0L else SystemClock.elapsedRealtime() - p.enteredAt

    private fun startPinger() {
        if (pinger?.isActive == true) return
        pinger = scope.launch {
            while (isActive) {
                delay(PING_MS)
                val snapshot = synchronized(lock) {
                    val cur = current
                    val id = cur?.id
                    if (!foreground || cur == null || id == null || cur.hidden || cur.closed) null else id to activeMs(cur)
                }
                if (snapshot != null) {
                    post {
                        put("action", "pageview_ping")
                        put("pageviewId", snapshot.first)
                        put("durationMs", snapshot.second)
                    }
                }
            }
        }
    }

    /** Long-lived random id that only tells new and returning installs apart. */
    private fun visitorKey(): Pair<String, Boolean> {
        prefs.getString(KEY_VISITOR, null)?.let { return it to false }
        val key = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_VISITOR, key).apply()
        return key to true
    }

    private suspend fun post(build: JsonObjectBuilder.() -> Unit): TrackReply? =
        runCatching { api.track(buildJsonObject(build)).body() }.getOrNull()

    private companion object {
        const val KEY_VISITOR = "visitor"
        const val PING_MS = 20_000L
        const val SESSION_IDLE_MS = 30 * 60 * 1000L
    }
}
