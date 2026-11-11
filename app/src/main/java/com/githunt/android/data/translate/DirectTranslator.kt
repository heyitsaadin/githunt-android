package com.githunt.android.data.translate

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Per-string results, in the same order as the request. */
internal class DirectResult(
    val translated: List<String>,
    /** True when the text was already in the target language. */
    val same: List<Boolean>,
    /** True when nothing could translate that string (its entry in [translated] is the original). */
    val failed: List<Boolean>,
    val viaGoogle: Int,
    val viaDevice: Int,
)

/**
 * Translates without the website: each string goes to Google straight from the phone, and any
 * string Google can't do (blocked, offline, too long) is tried on-device with ML Kit.
 *
 * Google's endpoint takes one string per request, so a batch fans out in parallel (at most
 * [MAX_PARALLEL] at once), where the website does that fan-out on its server.
 */
internal class DirectTranslator {

    private val google = GoogleTranslateClient()
    private val onDevice = OnDeviceTranslator()
    private val gate = Semaphore(MAX_PARALLEL)

    suspend fun translate(
        texts: List<String>,
        target: String,
        useGoogle: Boolean,
        useDevice: Boolean,
    ): DirectResult {
        val translated = texts.toMutableList()
        val same = MutableList(texts.size) { true }
        val failed = MutableList(texts.size) { true }
        val viaGoogle = AtomicInteger()
        val viaDevice = AtomicInteger()

        coroutineScope {
            texts.forEachIndexed { index, text ->
                launch {
                    if (text.isBlank()) {
                        failed[index] = false
                        return@launch
                    }
                    var hit: Translated? = null
                    if (useGoogle) {
                        hit = gate.withPermit { google.translate(text, target) }
                        if (hit != null) viaGoogle.incrementAndGet()
                    }
                    if (hit == null && useDevice) {
                        hit = onDevice.translate(text, target)
                        if (hit != null) viaDevice.incrementAndGet()
                    }
                    if (hit != null) {
                        translated[index] = hit.text
                        same[index] = hit.same
                        failed[index] = false
                    }
                }
            }
        }
        return DirectResult(translated, same, failed, viaGoogle.get(), viaDevice.get())
    }

    private companion object {
        const val MAX_PARALLEL = 8
    }
}
