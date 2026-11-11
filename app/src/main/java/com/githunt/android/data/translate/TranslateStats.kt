package com.githunt.android.data.translate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Running totals for one translation engine. One "request" is one batch the repository sent. */
data class EngineStats(
    val requests: Int = 0,
    val strings: Int = 0,
    val failed: Int = 0,
    val totalMs: Long = 0,
    val viaGoogle: Int = 0,
    val viaDevice: Int = 0,
) {
    /** One readable line for the Translation language screen. */
    fun summary(label: String): String {
        if (requests == 0) return "$label: no data yet."
        val perString = if (strings == 0) 0L else totalMs / strings
        val missed = if (strings == 0) 0 else failed * 100 / strings
        val sources = if (viaGoogle + viaDevice > 0) " Google $viaGoogle, on-device $viaDevice." else ""
        return "$label: $requests requests, avg ${totalMs / requests} ms each (~$perString ms per string), " +
            "$missed% left untranslated.$sources"
    }
}

/**
 * In-memory numbers for comparing the engines side by side. Both engines are measured the same
 * way: the time of one batch (one call to [TranslateApi.translate]) and how many of its strings
 * came back untranslated. They reset when the app restarts.
 */
object TranslateStats {
    const val SERVER = "server"
    const val DIRECT = "direct"

    private val _stats = MutableStateFlow<Map<String, EngineStats>>(emptyMap())
    val stats: StateFlow<Map<String, EngineStats>> = _stats.asStateFlow()

    fun record(
        engine: String,
        strings: Int,
        failed: Int,
        ms: Long,
        viaGoogle: Int = 0,
        viaDevice: Int = 0,
    ) {
        _stats.update { all ->
            val old = all[engine] ?: EngineStats()
            all + (engine to EngineStats(
                requests = old.requests + 1,
                strings = old.strings + strings,
                failed = old.failed + failed,
                totalMs = old.totalMs + ms,
                viaGoogle = old.viaGoogle + viaGoogle,
                viaDevice = old.viaDevice + viaDevice,
            ))
        }
    }

    fun reset() {
        _stats.value = emptyMap()
    }
}
