package com.githunt.android.data.engagement

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The Android side of the web app's personalization loop (lib/interest-state.js):
 *
 *  - [record] logs what the person does with a repo card (README, Ask AI, GitHub, share, clip,
 *    dwell, skip, not interested). Fire and forget, sent one at a time in order, because the
 *    server updates the taste profile with read-modify-write per keyword.
 *  - [interestScores] reads the decayed taste profile back so the feed can rank with it.
 *  - [hidden] holds repos marked "Not interested" this session, so every list can drop them at once
 *    (the server keeps its own 21 day suppression list for later loads).
 *
 * Nothing here checks the Personalization toggle: the server is the authority. With it off, writes
 * are ignored and the score map comes back empty.
 */
class EngagementRepository(
    private val api: EngagementApi,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<EngagementBody>(capacity = 200, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val lock = Mutex()
    @Volatile private var cached: Map<String, Double>? = null
    @Volatile private var dirty = true

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    init {
        scope.launch {
            for (event in outbox) {
                try {
                    apiCall(json) { api.record(event) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best effort: a lost signal is not worth bothering anyone about.
                }
                // The profile changed on the server, so the cached copy is out of date.
                dirty = true
            }
        }
    }

    fun record(item: RepoItem, event: String, dwellSeconds: Double? = null) {
        outbox.trySend(EngagementBody(item.toEngagementRepo(), event, dwellSeconds))
    }

    /** Logs the strongest negative signal and hides the repo everywhere right away. */
    fun notInterested(item: RepoItem) {
        record(item, EngagementEvent.NotInterested)
        _hidden.update { it + item.fullName }
    }

    /** The person's current taste profile. Re-fetched only when something was recorded since. */
    suspend fun interestScores(forceRefresh: Boolean = false): Map<String, Double> = lock.withLock {
        val current = cached
        if (current != null && !dirty && !forceRefresh) {
            current
        } else {
            try {
                when (val result = apiCall(json) { api.interest() }) {
                    is ApiResult.Ok -> {
                        val fresh = result.value.scores
                        cached = fresh
                        dirty = false
                        fresh
                    }
                    is ApiResult.Err -> current ?: emptyMap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                current ?: emptyMap()
            }
        }
    }

    /** Forget the cached profile (after Personalization is switched off or cleared in Settings). */
    fun reset() {
        cached = null
        dirty = true
    }
}

/**
 * The server only reads full_name, owner, name, language and topics from the repo it is sent, so
 * send just those instead of the whole card (README snippet and image list included).
 */
private fun RepoItem.toEngagementRepo(): JsonObject = buildJsonObject {
    put("full_name", JsonPrimitive(fullName))
    put("owner", JsonPrimitive(owner))
    put("name", JsonPrimitive(fullName.substringAfter('/')))
    language?.let { put("language", JsonPrimitive(it)) }
    put("topics", JsonArray(topics.map { JsonPrimitive(it) }))
}
