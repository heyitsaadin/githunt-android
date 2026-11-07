package com.githunt.android.data.translate

import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** The website's translation endpoint: POST /api/translate { texts, target }. */
interface TranslateApi {
    @POST("api/translate")
    suspend fun translate(@Body body: TranslateRequest): Response<TranslateResponse>
}

@Serializable
data class TranslateRequest(
    val texts: List<String>,
    val target: String,
)

/**
 * Per-string arrays, in the same order as the request:
 * [translated] the translation (the original when it failed or was already the target language),
 * [sameLanguage] true when there was nothing to translate, [failed] true when no backend could do it.
 */
@Serializable
data class TranslateResponse(
    val translated: List<String> = emptyList(),
    val sameLanguage: List<Boolean> = emptyList(),
    val failed: List<Boolean> = emptyList(),
    val partial: Boolean = false,
    val error: String? = null,
)

/** A card's text after translation. Anything that could not be translated keeps its original. */
data class TranslatedCard(
    val description: String?,
    val snippet: String?,
    val topics: List<String>,
    /** True only when at least one string was really translated (not already the target language). */
    val changed: Boolean,
)

/** A README after translation. [changed] is true only when some piece really was translated. */
data class MarkdownTranslation(
    val markdown: String,
    val changed: Boolean,
)

/**
 * Translates the text on repo cards into the language chosen in Settings.
 *
 * The source language is detected by the server, so only the target is sent. Results are cached
 * in memory (including "already in that language"), so scrolling back or reopening a card costs
 * nothing. Strings that failed are not cached, so they get another try later.
 */
class TranslateRepository(private val api: TranslateApi) {

    private class Entry(val text: String, val same: Boolean)

    private val cache = LruCache<String, Entry>(CACHE_SIZE)

    // The server leans on free translation backends that rate-limit, and a feed page has ~20 cards
    // asking at once. A few at a time keeps them happy.
    private val gate = Semaphore(MAX_CONCURRENT_REQUESTS)

    private fun key(target: String, text: String) = "$target\u0000$text"

    /**
     * Translates a card's description, README excerpt and the topics it shows, in one request.
     * Never throws (except for coroutine cancellation): on any failure the original text comes back.
     */
    suspend fun translateCard(
        description: String?,
        snippet: String?,
        topics: List<String>,
        target: String,
    ): TranslatedCard {
        val original = TranslatedCard(description, snippet, topics, changed = false)
        if (target == OFF) return original

        val texts = ArrayList<String>()
        fun add(value: String?): Int {
            if (value.isNullOrBlank()) return -1
            texts.add(value)
            return texts.lastIndex
        }

        val descriptionIndex = add(description)
        val snippetIndex = add(snippet)
        // Only the chips the card actually shows.
        val topicIndexes = topics.mapIndexed { i, topic -> if (i < SHOWN_TOPICS) add(topic) else -1 }
        if (texts.isEmpty()) return original

        val results = translateAll(texts, target)
        fun pick(index: Int, fallback: String?): String? =
            if (index >= 0) results[index]?.text ?: fallback else fallback

        return TranslatedCard(
            description = pick(descriptionIndex, description),
            snippet = pick(snippetIndex, snippet),
            topics = topics.mapIndexed { i, topic -> pick(topicIndexes[i], topic) ?: topic },
            changed = results.any { it != null && !it.same },
        )
    }

    /**
     * Translates a README in place: code, links, tables and other structure are never touched, only
     * the readable words (see MarkdownSegmenter). Pieces go out top to bottom in small batches and
     * [onProgress] gets the best markdown so far after each one, so the top of the README shows up
     * quickly. Whatever could not be translated keeps its original text.
     *
     * Never throws (except for coroutine cancellation). Calling it again only re-requests what is
     * missing, because every piece that did translate is cached.
     */
    suspend fun translateMarkdown(
        markdown: String,
        target: String,
        onProgress: (String) -> Unit,
    ): MarkdownTranslation {
        val unchanged = MarkdownTranslation(markdown, changed = false)
        if (target == OFF || markdown.isBlank()) return unchanged

        val plan = withContext(Dispatchers.Default) { planMarkdown(markdown, target) }
        val results = HashMap<String, String>()
        for (text in plan.order) {
            cache.get(key(target, text))?.let { results[text] = it.text }
        }

        // What still needs the network, in document order, within the character budget.
        val todo = ArrayList<String>()
        var budget = README_CHAR_BUDGET
        for (text in plan.order) {
            if (text in results) continue
            if (budget - text.length < 0) break
            budget -= text.length
            todo.add(text)
        }

        // A small first batch so something shows up fast.
        val batches = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var currentChars = 0
        for (text in todo) {
            val limit = if (batches.isEmpty()) FIRST_BATCH_SIZE else BATCH_SIZE
            if (current.isNotEmpty() && (current.size >= limit || currentChars + text.length > BATCH_CHAR_LIMIT)) {
                batches.add(current)
                current = ArrayList()
                currentChars = 0
            }
            current.add(text)
            currentChars += text.length
        }
        if (current.isNotEmpty()) batches.add(current)

        if (results.isNotEmpty()) onProgress(assembleMarkdown(plan.slots, results)) // cached pieces show instantly

        var strikes = 0
        for (batch in batches) {
            val fetched = fetch(batch, target)
            var ok = 0
            if (fetched != null) {
                batch.forEachIndexed { i, text ->
                    val entry = fetched[i] ?: return@forEachIndexed
                    results[text] = entry.text
                    cache.put(key(target, text), entry)
                    ok++
                }
            }
            // The backends refusing twice in a row means they're out of capacity: stop here and
            // leave the rest in the original language.
            strikes = if (ok == 0) strikes + 1 else 0
            if (strikes >= 2) break
            emitProgress()
        }

        val changed = results.any { (text, translated) -> text != translated }
        return MarkdownTranslation(assembleMarkdown(plan.slots, results), changed)
    }

    /** One entry per input string; null where it could not be translated. */
    private suspend fun translateAll(texts: List<String>, target: String): List<Entry?> {
        val out = arrayOfNulls<Entry>(texts.size)
        val missing = LinkedHashMap<String, MutableList<Int>>()
        texts.forEachIndexed { i, text ->
            val hit = cache.get(key(target, text))
            if (hit != null) {
                out[i] = hit
            } else if (text.length <= MAX_STRING_CHARS) {
                // Longer strings are skipped: the server would refuse them anyway.
                missing.getOrPut(text) { mutableListOf() }.add(i)
            }
        }

        if (missing.isNotEmpty()) {
            val unique = missing.keys.toList()
            unique.chunked(MAX_STRINGS).forEach { chunk ->
                val fetched = fetch(chunk, target) ?: return@forEach
                chunk.forEachIndexed { j, text ->
                    val entry = fetched[j] ?: return@forEachIndexed
                    cache.put(key(target, text), entry)
                    missing[text]?.forEach { index -> out[index] = entry }
                }
            }
        }
        return out.toList()
    }

    private suspend fun fetch(texts: List<String>, target: String): List<Entry?>? {
        return gate.withPermit {
            try {
                val response = api.translate(TranslateRequest(texts, target))
                val body = response.body()
                if (!response.isSuccessful || body == null || body.translated.size != texts.size) {
                    return@withPermit null
                }
                texts.indices.map { i ->
                    if (body.failed.getOrNull(i) == true) {
                        null
                    } else {
                        Entry(body.translated[i], body.sameLanguage.getOrNull(i) ?: false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private companion object {
        const val OFF = "off"
        const val SHOWN_TOPICS = 3
        const val CACHE_SIZE = 5000
        const val MAX_CONCURRENT_REQUESTS = 3
        // Server limits (app/api/translate/route.js).
        const val MAX_STRINGS = 200
        const val MAX_STRING_CHARS = 4000

        // README translation (lib/translate-markdown.js): how much of one README goes through the
        // free translation backends, and how it is batched.
        const val README_CHAR_BUDGET = 16_000
        const val FIRST_BATCH_SIZE = 10
        const val BATCH_SIZE = 24
        const val BATCH_CHAR_LIMIT = 3_200
    }
}
