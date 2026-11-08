package com.githunt.android.data.translate

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

/**
 * One chunk of a README while it is being translated.
 *
 * [pending] is true while the chunk waits for its translation: the screen shows a skeleton for it.
 * When it is done, [translated] holds the translated markdown, or is null when nothing in the chunk
 * needed (or could get) a translation, in which case the original is shown as it is.
 */
data class ReadmeBlock(
    val original: String,
    val translated: String?,
    val pending: Boolean,
)

/**
 * How a README translation ended. [changed] is true when some text really was translated;
 * [failedPieces] counts the pieces no translation backend could do.
 */
data class ReadmeTranslation(
    val changed: Boolean,
    val failedPieces: Int,
)

/**
 * Translates the text on repo cards, and READMEs, into the language chosen in Settings.
 *
 * The source language is detected by the server, so only the target is sent. Results are cached
 * in memory (including "already in that language"), so scrolling back or reopening a card costs
 * nothing. Strings that failed are not cached, so they get another try later.
 */
class TranslateRepository(private val api: TranslateApi) {

    private class Entry(val text: String, val same: Boolean)

    private class PlannedBlock(val original: String, val plan: MarkdownPlan)

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
     * Translates a README in place, one block at a time.
     *
     * The README is cut into blocks (see MarkdownSegmenter) and translated top to bottom in small
     * batches. Code, links, tables and other structure are never touched, only the readable words.
     *
     * [onPlan] is called once, right away, with every block: the ones with text to translate are
     * [ReadmeBlock.pending] (unless the cache already had everything), the rest are final as they
     * are. After that, [onBlock] is called for each block the moment its translation is complete, so
     * the screen can swap that block's skeleton for the translated text and move on to the next.
     * Both callbacks run on the caller's thread; the heavy work runs on background threads.
     *
     * Never throws (except for coroutine cancellation). Anything that could not be translated keeps
     * its original text, and a block that is still waiting when the backends give up (or the length
     * budget runs out) stops being pending with whatever did get translated.
     */
    suspend fun translateReadme(
        markdown: String,
        target: String,
        onPlan: (List<ReadmeBlock>) -> Unit,
        onBlock: (index: Int, block: ReadmeBlock) -> Unit,
    ): ReadmeTranslation {
        val nothing = ReadmeTranslation(changed = false, failedPieces = 0)
        if (target == OFF || markdown.isBlank()) return nothing

        val planned = withContext(Dispatchers.Default) {
            val defs = collectReferenceLabels(markdown)
            splitMarkdownBlocks(markdown).map { block ->
                PlannedBlock(block, planMarkdown(block, target, defs))
            }
        }
        if (planned.isEmpty()) return nothing

        // Everything the cache already knows.
        val results = HashMap<String, String>()
        for (block in planned) {
            for (text in block.plan.order) {
                if (text in results) continue
                cache.get(key(target, text))?.let { results[text] = it.text }
            }
        }

        fun isComplete(block: PlannedBlock) = block.plan.order.all { it in results }

        // The block's translated markdown, or null when nothing in it actually changed.
        suspend fun translatedText(block: PlannedBlock): String? {
            val changed = block.plan.order.any { text -> results[text]?.let { it != text } == true }
            if (!changed) return null
            return withContext(Dispatchers.Default) { assembleMarkdown(block.plan.slots, results) }
        }

        val finished = BooleanArray(planned.size) // blocks already reported as final
        val initial = ArrayList<ReadmeBlock>(planned.size)
        for ((i, block) in planned.withIndex()) {
            if (block.plan.order.isEmpty()) {
                finished[i] = true // nothing to translate: shown as it is
                initial.add(ReadmeBlock(block.original, null, pending = false))
            } else if (isComplete(block)) {
                finished[i] = true // everything was cached
                initial.add(ReadmeBlock(block.original, translatedText(block), pending = false))
            } else {
                initial.add(ReadmeBlock(block.original, null, pending = true))
            }
        }
        onPlan(initial)

        // What still needs the network, in document order, within the character budget.
        val order = LinkedHashSet<String>()
        for (block in planned) order.addAll(block.plan.order)
        val todo = ArrayList<String>()
        var budget = README_CHAR_BUDGET
        for (text in order) {
            if (text in results) continue
            if (budget - text.length < 0) break
            budget -= text.length
            todo.add(text)
        }

        // A small first batch so the first block is done fast.
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
            // Every block whose pieces have all arrived is handed over now, top to bottom.
            for ((i, block) in planned.withIndex()) {
                if (finished[i] || !isComplete(block)) continue
                finished[i] = true
                onBlock(i, ReadmeBlock(block.original, translatedText(block), pending = false))
            }
            // The backends refusing twice in a row means they're out of capacity: stop here and
            // leave the rest in the original language.
            strikes = if (ok == 0) strikes + 1 else 0
            if (strikes >= 2) break
        }

        // Whatever is still waiting stops showing a skeleton: the pieces that did arrive are
        // translated, the rest stays in the original language.
        for ((i, block) in planned.withIndex()) {
            if (finished[i]) continue
            finished[i] = true
            onBlock(i, ReadmeBlock(block.original, translatedText(block), pending = false))
        }

        val failed = order.count { it !in results }
        val changed = order.any { text -> results[text]?.let { it != text } == true }
        return ReadmeTranslation(changed = changed, failedPieces = failed)
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
