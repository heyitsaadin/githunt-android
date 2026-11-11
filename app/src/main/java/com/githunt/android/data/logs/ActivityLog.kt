package com.githunt.android.data.logs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

enum class LogCategory(val label: String) {
    App("App"),
    Network("Network"),
    Navigation("Navigation"),
    Settings("Settings"),
    Account("Account"),
}

enum class LogLevel { Info, Warn, Error }

data class LogEntry(
    val time: Long,
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
)

/**
 * An on-device activity log, like the debug logs in the Discord app: every network request, every
 * setting the person changes, sign-ins and sign-outs, app open / close and crashes.
 *
 * Entries are kept in memory (newest last, capped at [MAX_ENTRIES]) and mirrored to a small file in
 * the app's private storage so they survive the app being closed. Nothing ever leaves the phone.
 *
 * Never log secrets here: no passwords, tokens, request or response bodies.
 */
object ActivityLog {
    const val MAX_ENTRIES = 1000
    private const val MAX_MESSAGE = 500
    private const val FILE_NAME = "activity.log"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Oldest first. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var file: File? = null
    private var linesOnDisk = 0
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "activity-log").apply { isDaemon = true }
    }

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        val loaded = runCatching {
            if (target.exists()) target.readLines().takeLast(MAX_ENTRIES).mapNotNull(::parse) else emptyList()
        }.getOrDefault(emptyList())
        linesOnDisk = loaded.size
        _entries.value = loaded
    }

    fun log(category: LogCategory, message: String, level: LogLevel = LogLevel.Info) {
        record(category, message, level, sync = false)
    }

    /** Written to disk before returning. Used when the process is about to die. */
    fun logNow(category: LogCategory, message: String, level: LogLevel = LogLevel.Error) {
        record(category, message, level, sync = true)
    }

    fun clear() {
        _entries.value = emptyList()
        io.execute {
            runCatching { file?.writeText("") }
            linesOnDisk = 0
        }
    }

    private fun record(category: LogCategory, message: String, level: LogLevel, sync: Boolean) {
        val entry = LogEntry(
            time = System.currentTimeMillis(),
            category = category,
            level = level,
            message = message.replace('\n', ' ').replace('\t', ' ').take(MAX_MESSAGE),
        )
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
        if (sync) persist(entry) else io.execute { persist(entry) }
    }

    @Synchronized
    private fun persist(entry: LogEntry) {
        val target = file ?: return
        runCatching {
            target.appendText(encode(entry) + "\n")
            linesOnDisk++
            // Keep the file from growing forever: rewrite it from memory once it's well over the cap.
            if (linesOnDisk > MAX_ENTRIES + 250) {
                val current = _entries.value
                target.writeText(current.joinToString("\n", postfix = "\n") { encode(it) })
                linesOnDisk = current.size
            }
        }
    }

    private fun encode(e: LogEntry): String = "${e.time}\t${e.category.name}\t${e.level.name}\t${e.message}"

    private fun parse(line: String): LogEntry? {
        val parts = line.split('\t', limit = 4)
        if (parts.size < 4) return null
        val time = parts[0].toLongOrNull() ?: return null
        val category = LogCategory.values().firstOrNull { it.name == parts[1] } ?: return null
        val level = LogLevel.values().firstOrNull { it.name == parts[2] } ?: return null
        return LogEntry(time, category, level, parts[3])
    }
}

/**
 * Records every request the app makes: method, path, status and how long it took.
 * Query strings, headers and bodies are deliberately left out (they can hold passwords and tokens).
 */
class ActivityLogInterceptor(private val apiHost: String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val where = if (apiHost == null || host == apiHost) "" else "$host"
        val label = "${request.method} $where${request.url.encodedPath}"
        val start = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - start) / 1_000_000
            val level = when {
                response.code >= 500 -> LogLevel.Error
                response.code >= 400 -> LogLevel.Warn
                else -> LogLevel.Info
            }
            ActivityLog.log(LogCategory.Network, "$label \u2192 ${response.code} (${ms} ms)", level)
            return response
        } catch (e: IOException) {
            val ms = (System.nanoTime() - start) / 1_000_000
            ActivityLog.log(
                LogCategory.Network,
                "$label failed after $ms ms: ${e.javaClass.simpleName}",
                LogLevel.Error,
            )
            throw e
        }
    }
}
