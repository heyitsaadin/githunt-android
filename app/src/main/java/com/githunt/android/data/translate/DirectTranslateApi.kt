package com.githunt.android.data.translate

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Translates without the website: each string goes to Google straight from the phone, and any
 * string Google can't do (blocked, offline, too long) is tried on-device with ML Kit.
 *
 * It stands in for the website's [TranslateApi], answering in the same shape, so
 * [TranslateRepository] (batching, cache, README blocks, skeletons) works with it unchanged.
 * Google's endpoint takes one string per request, so a batch fans out in parallel (at most
 * [MAX_PARALLEL] at once), where the website did that fan-out on its server.
 */
class DirectTranslateApi : TranslateApi {

    private val google = GoogleTranslateClient()
    private val onDevice = OnDeviceTranslator()
    private val gate = Semaphore(MAX_PARALLEL)

    override suspend fun translate(body: TranslateRequest): Response<TranslateResponse> {
        val started = SystemClock.elapsedRealtime()
        val texts = body.texts
        val target = body.target
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
                    var hit = gate.withPermit { google.translate(text, target) }
                    if (hit != null) {
                        viaGoogle.incrementAndGet()
                    } else {
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

        val failedCount = failed.count { it }
        TranslateStats.record(
            engine = TranslateStats.DIRECT,
            strings = texts.size,
            failed = failedCount,
            ms = SystemClock.elapsedRealtime() - started,
            viaGoogle = viaGoogle.get(),
            viaDevice = viaDevice.get(),
        )
        return Response.success(
            TranslateResponse(
                translated = translated,
                sameLanguage = same,
                failed = failed,
                partial = failedCount in 1 until texts.size,
            ),
        )
    }

    private companion object {
        const val MAX_PARALLEL = 8
    }
}

/** Wraps an engine and records how long each batch took and how much came back untranslated. */
class TimedTranslateApi(
    private val engine: String,
    private val inner: TranslateApi,
) : TranslateApi {

    override suspend fun translate(body: TranslateRequest): Response<TranslateResponse> {
        val started = SystemClock.elapsedRealtime()
        val all = body.texts.size
        return try {
            val response = inner.translate(body)
            val data = response.body()
            val failed = if (response.isSuccessful && data != null) data.failed.count { it } else all
            TranslateStats.record(engine, all, failed, SystemClock.elapsedRealtime() - started)
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TranslateStats.record(engine, all, all, SystemClock.elapsedRealtime() - started)
            throw e
        }
    }
}
