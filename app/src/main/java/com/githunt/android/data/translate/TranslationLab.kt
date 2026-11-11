package com.githunt.android.data.translate

import android.os.SystemClock
import kotlinx.coroutines.CancellationException

/** The ways of translating that the Translation language screen compares side by side. */
enum class LabEngine(val label: String) {
    Website("Website (the current way)"),
    Direct("Direct Google, on-device backup"),
    OnDevice("On-device only (ML Kit)"),
}

/** One engine's answer to one run. [lines] are in the same order as the input. */
class LabRun(
    val engine: LabEngine,
    val lines: List<String>,
    val ms: Long,
    val note: String,
)

/**
 * Translates the same lines with each engine and times it, so the engines can be compared on the
 * phone with real text, on the real network. Nothing is cached, so every run is a fair one.
 */
class TranslationLab(private val website: TranslateApi) {

    private val direct = DirectTranslator()

    suspend fun run(engine: LabEngine, texts: List<String>, target: String): LabRun = when (engine) {
        LabEngine.Website -> runWebsite(texts, target)
        LabEngine.Direct -> runDirect(engine, texts, target, useGoogle = true, useDevice = true)
        LabEngine.OnDevice -> runDirect(engine, texts, target, useGoogle = false, useDevice = true)
    }

    private suspend fun runWebsite(texts: List<String>, target: String): LabRun {
        val started = SystemClock.elapsedRealtime()
        val body = try {
            val response = website.translate(TranslateRequest(texts, target))
            response.body()?.takeIf { response.isSuccessful && it.translated.size == texts.size }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val ms = SystemClock.elapsedRealtime() - started
        if (body == null) return LabRun(LabEngine.Website, texts, ms, "The website did not answer.")

        val lines = texts.mapIndexed { i, text ->
            if (body.failed.getOrNull(i) == true) text else body.translated[i]
        }
        val failed = texts.indices.count { body.failed.getOrNull(it) == true }
        val same = texts.indices.count { body.failed.getOrNull(it) != true && body.sameLanguage.getOrNull(it) == true }
        return LabRun(LabEngine.Website, lines, ms, summary(texts.size, failed, same))
    }

    private suspend fun runDirect(
        engine: LabEngine,
        texts: List<String>,
        target: String,
        useGoogle: Boolean,
        useDevice: Boolean,
    ): LabRun {
        val started = SystemClock.elapsedRealtime()
        val result = direct.translate(texts, target, useGoogle, useDevice)
        val ms = SystemClock.elapsedRealtime() - started

        val failed = result.failed.count { it }
        val same = texts.indices.count { !result.failed[it] && result.same[it] }
        val parts = ArrayList<String>()
        parts.add(summary(texts.size, failed, same))
        if (useGoogle) parts.add("Google ${result.viaGoogle}, on-device ${result.viaDevice}")
        if (useDevice && failed > 0) {
            parts.add("On-device models download on Wi-Fi the first time, so run again in a minute")
        }
        return LabRun(engine, result.translated, ms, parts.joinToString(". "))
    }

    private fun summary(total: Int, failed: Int, same: Int): String {
        val parts = ArrayList<String>()
        parts.add("${total - failed} of $total answered")
        if (same > 0) parts.add("$same already in that language")
        return parts.joinToString(", ")
    }
}
