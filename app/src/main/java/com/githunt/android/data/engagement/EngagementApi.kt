package com.githunt.android.data.engagement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * What the website's recordEngagement() posts to POST /api/engagement. The server turns each event
 * into the signed-in person's taste profile (languages, topics and language x topic pairs) that
 * ranks their feed. It answers 204 and writes nothing for guests, and nothing while the person has
 * Personalization switched off in Settings.
 */
interface EngagementApi {

    @POST("api/engagement")
    suspend fun record(@Body body: EngagementPayload): Response<Unit>
}

/**
 * @param repo the repo the event is about. The server only reads full_name, owner, name, language
 *   and topics from it, so that is all that is sent.
 * @param eventType one of [EngagementEvent].
 * @param dwellSeconds only for [EngagementEvent.DWELL] (required there) and informational for skips.
 */
@Serializable
data class EngagementPayload(
    val repo: JsonObject,
    @SerialName("event_type") val eventType: String,
    @SerialName("dwell_seconds") val dwellSeconds: Double? = null,
)

/** The event names the server accepts (EVENT_WEIGHTS in the website's lib/interest-scoring.js). */
object EngagementEvent {
    /** The README was opened in the card. */
    const val README = "readme"
    const val ASK_AI = "ask_ai"
    const val GITHUB_CLICK = "github_click"
    const val SHARE = "share"
    const val CLIP = "clip"
    const val UNCLIP = "unclip"

    /** The card was on screen for a real look (4 seconds or more). */
    const val DWELL = "dwell"

    /** The card was shown and scrolled past with no action: a mild negative signal. */
    const val SKIP = "skip"

    /** "Not interested" in the card's menu: the strongest negative signal. */
    const val NOT_INTERESTED = "not_interested"
}
