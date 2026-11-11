package com.githunt.android.data.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The on-device translator (ML Kit): detects the source language, then translates offline.
 *
 * It is lower quality than Google's cloud model but needs no network and has no quota. Each
 * language needs a model of about 30 MB, downloaded the first time a pair is used, on Wi-Fi only
 * (change [DownloadConditions] below to allow mobile data). A pair whose model is not ready within
 * a few seconds is skipped (returns null) while the download carries on in the background, so one
 * slow download never stalls a whole README.
 */
internal class OnDeviceTranslator {

    private class LanguagePair(val translator: Translator) {
        @Volatile
        var ready = false
        var preparing: Task<Void>? = null
    }

    private val identifier by lazy { LanguageIdentification.getClient() }
    private val pairs = HashMap<String, LanguagePair>()
    private val gate = Semaphore(2)

    /** Translates one string, or returns null when it can't be done on the device right now. */
    suspend fun translate(text: String, target: String): Translated? {
        val targetTag = modelTag(target) ?: return null
        val detected = try {
            identifier.identifyLanguage(text).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (detected == null || detected == "und") return null
        val sourceTag = TranslateLanguage.fromLanguageTag(detected) ?: return null
        if (sourceTag == targetTag) return Translated(text, same = true)

        val translator = prepare(sourceTag, targetTag) ?: return null
        val out = gate.withPermit {
            withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
                try {
                    translator.translate(text).awaitTask()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
        return if (out.isNullOrBlank()) null else Translated(out, same = false)
    }

    /** The translator for a language pair once its models are on the device, else null. */
    private suspend fun prepare(source: String, target: String): Translator? {
        val entry = synchronized(pairs) {
            pairs.getOrPut("$source>$target") {
                LanguagePair(
                    Translation.getClient(
                        TranslatorOptions.Builder()
                            .setSourceLanguage(source)
                            .setTargetLanguage(target)
                            .build(),
                    ),
                )
            }
        }
        if (entry.ready) return entry.translator

        val task = synchronized(entry) {
            entry.preparing ?: entry.translator
                .downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
                .also { started ->
                    entry.preparing = started
                    started.addOnSuccessListener { entry.ready = true }
                    // A failed download is retried the next time the pair is needed.
                    started.addOnFailureListener { synchronized(entry) { entry.preparing = null } }
                }
        }
        withTimeoutOrNull(PREPARE_WAIT_MS) {
            try {
                task.awaitTask()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return if (entry.ready) entry.translator else null
    }

    /** ML Kit's code for one of the app's language codes, or null when it has no model for it. */
    private fun modelTag(code: String): String? = when (code) {
        "zh-CN" -> TranslateLanguage.CHINESE
        "zh-TW" -> null // ML Kit has no Traditional Chinese model
        else -> TranslateLanguage.fromLanguageTag(code)
    }

    private companion object {
        const val PREPARE_WAIT_MS = 4_000L
        const val TRANSLATE_TIMEOUT_MS = 10_000L
    }
}

private suspend fun <T> Task<T>.awaitTask(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
