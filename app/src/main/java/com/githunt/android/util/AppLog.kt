package com.githunt.android.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A tiny in-process log collector, independent of android.util.Log /
 * Logcat. Exists because reading another app's Logcat output requires
 * android.permission.READ_LOGS, which can only be granted via `adb shell
 * pm grant` from a computer -- not from the phone alone. This collector
 * writes into the app's own memory and private storage instead, which
 * needs no special permission, so logs (including ones from before the
 * user thought to look) can be exported straight from Settings.
 *
 * [init] must be called as the very first thing in Application.onCreate(),
 * before any other component runs, so startup activity (network client
 * construction, cookie jar init, first /api/auth/me check, etc.) is
 * captured too -- not just events after the user opens the log screen.
 */
object AppLog {

    private const val MAX_LINES = 4000
    private const val LOG_FILE_NAME = "githunt_session_log.txt"

    private val buffer = ConcurrentLinkedDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null
    @Volatile private var sessionStartedAtMs: Long = 0L

    /** Call once, first line of Application.onCreate(). */
    fun init(context: Context) {
        if (logFile != null) return // already initialized (shouldn't happen twice, but be safe)
        sessionStartedAtMs = System.currentTimeMillis()
        logFile = File(context.filesDir, LOG_FILE_NAME).also {
            // Start each process run with a clean file; the in-memory buffer
            // is what backs "this session's" view, so the file is really
            // just an export target, not a growing history across restarts.
            it.writeText("")
        }
        i("AppLog", "===== App process started =====")
        i("AppLog", "Time: ${java.util.Date(sessionStartedAtMs)}")
        i("AppLog", "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        i("AppLog", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }

    fun d(tag: String, message: String) = write("D", tag, message)
    fun i(tag: String, message: String) = write("I", tag, message)
    fun w(tag: String, message: String) = write("W", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        write("E", tag, full)
    }

    /** All lines currently held in memory, oldest first. */
    fun snapshot(): List<String> = buffer.toList()

    /** Full text of the current session's log, suitable for sharing/saving. */
    fun exportText(): String = snapshot().joinToString("\n")

    /** The on-disk file path, kept in sync with every write() call. */
    fun exportFile(): File? = logFile

    fun clear() {
        buffer.clear()
        logFile?.writeText("")
    }

    private fun write(level: String, tag: String, message: String) {
        val line = "${timeFormat.format(java.util.Date())} $level/$tag: $message"

        buffer.addLast(line)
        while (buffer.size > MAX_LINES) {
            buffer.pollFirst()
        }

        // Best-effort append to disk; a failure here (e.g. disk full) should
        // never crash the app, since this is a debugging aid, not a core
        // feature.
        try {
            logFile?.appendText(line + "\n")
        } catch (_: Exception) {
            // Swallow -- nothing useful to do if we can't write our own debug log.
        }
    }
}
