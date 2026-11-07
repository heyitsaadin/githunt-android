package com.githunt.android.data.engagement

import com.githunt.android.data.feed.RepoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reports what the person does with repo cards to the backend, like the website's
 * `recordEngagement()`. This is the half of Personalization that makes it do anything: the feed is
 * ranked on the server from these events, so without them the profile never learns.
 *
 * Every call is fire and forget. It never blocks the screen, never throws, and a failed request is
 * just dropped (the website does the same). Whether anything is stored is decided by the server:
 * it ignores events while Personalization is off in Settings, and the Clear button deletes them.
 */
class EngagementTracker(private val api: EngagementApi) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())

    /** Full names of repos marked "Not interested" in this app session; the feed drops them at once. */
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    fun record(item: RepoItem, event: String, dwellSeconds: Double? = null) {
        val payload = EngagementPayload(
            repo = item.toEngagementRepo(),
            eventType = event,
            dwellSeconds = dwellSeconds,
        )
        scope.launch { runCatching { api.record(payload) } }
    }

    /**
     * "Not interested": tells the server (it scores the repo's language and topics down and stops
     * sending this repo for a while) and hides the card right now.
     */
    fun notInterested(item: RepoItem) {
        _hidden.update { it + item.fullName }
        record(item, EngagementEvent.NOT_INTERESTED)
    }
}

/** The few fields the server's scoring reads (full_name, owner, name, language, topics). */
private fun RepoItem.toEngagementRepo(): JsonObject = buildJsonObject {
    put("full_name", fullName)
    put("owner", owner)
    put("name", fullName.substringAfter('/'))
    language?.let { put("language", it) }
    put("topics", JsonArray(topics.map { JsonPrimitive(it) }))
}
