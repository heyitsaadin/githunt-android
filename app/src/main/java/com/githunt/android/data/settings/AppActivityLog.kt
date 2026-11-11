package com.githunt.android.data.settings

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.PrintWriter
import java.io.StringWriter

data class ActivityLogEntry(
    val id: String,
    val timestamp: Long,
    val category: String,
    val action: String,
    val details: String,
    val status: String = "info",
)

class AppActivityLog(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("githunt_activity_log", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _entries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<ActivityLogEntry>> = _entries.asStateFlow()

    init {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
                val report = buildString {
                    appendLine("GitHunt crash report")
                    appendLine("Time: ${Date(System.currentTimeMillis())}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("App version: ${com.githunt.android.BuildConfig.VERSION_NAME}")
                    appendLine()
                    append(trace)
                }
                preferences.edit().putString(KEY_CRASH_REPORT, report).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latestCrashReport(): String? = preferences.getString(KEY_CRASH_REPORT, null)

    fun clearCrashReport() {
        preferences.edit().remove(KEY_CRASH_REPORT).apply()
    }

    fun record(category: String, action: String, details: String = "", status: String = "info") {
        synchronized(lock) {
            val next = (listOf(ActivityLogEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), category, action, details, status)) + _entries.value).take(MAX_ENTRIES)
            _entries.value = next
            preferences.edit().putString(KEY_ENTRIES, next.joinToString("\n") { encode(it) }).apply()
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            preferences.edit().remove(KEY_ENTRIES).apply()
        }
    }

    private fun readEntries(): List<ActivityLogEntry> = runCatching {
        preferences.getString(KEY_ENTRIES, null).orEmpty().lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::decode)
            .take(MAX_ENTRIES)
            .toList()
    }.getOrDefault(emptyList())

    private fun encode(entry: ActivityLogEntry): String = listOf(
        entry.id, entry.timestamp.toString(), entry.category, entry.action, entry.status, entry.details,
    ).joinToString("\t") { it.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n") }

    private fun decode(line: String): ActivityLogEntry? = runCatching {
        val parts = line.split("\t")
        if (parts.size != 6) return null
        fun unescape(value: String) = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
        ActivityLogEntry(parts[0], parts[1].toLong(), parts[2], parts[3], unescape(parts[5]), parts[4])
    }.getOrNull()

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_CRASH_REPORT = "latest_crash_report"
        const val MAX_ENTRIES = 300
    }
}

/** Records request lifecycle metadata only. URLs are reduced to paths; headers and bodies are never stored. */
class ActivityLogInterceptor(private val log: AppActivityLog) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val label = "${request.method} $path"
        val started = SystemClock.elapsedRealtime()
        log.record("Network", "Request started", label, "running")
        return try {
            val response = chain.proceed(request)
            val elapsed = SystemClock.elapsedRealtime() - started
            log.record("Network", "Request completed", "$label · HTTP ${response.code} · ${elapsed}ms", if (response.isSuccessful) "success" else "warning")
            response
        } catch (error: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - started
            log.record("Network", "Request failed", "$label · ${error.javaClass.simpleName} · ${elapsed}ms", "error")
            throw error
        }
    }
}

fun ActivityLogEntry.displayTime(): String =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
