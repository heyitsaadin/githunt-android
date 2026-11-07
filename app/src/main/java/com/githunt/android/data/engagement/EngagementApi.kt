package com.githunt.android.data.engagement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Event names the web backend's EVENT_WEIGHTS (lib/interest-scoring.js) understands. */
object EngagementEvent {
    const val Readme = "readme"
    const val AskAi = "ask_ai"
    const val GithubClick = "github_click"
    const val Share = "share"
    const val Clip = "clip"
    const val Unclip = "unclip"
    const val Dwell = "dwell"
    const val Skip = "skip"
    const val NotInterested = "not_interested"
}

/** POST /api/engagement body: one event about one repo. */
@Serializable
data class EngagementBody(
    val repo: JsonObject,
    @SerialName("event_type") val eventType: String,
    @SerialName("dwell_seconds") val dwellSeconds: Double? = null,
)

/** GET /api/engagement/interest: { "language:python": 1.2, "domain:ai": -0.8, "pair:python:ai": 0.4 } */
@Serializable
data class InterestResponse(val scores: Map<String, Double> = emptyMap())

/**
 * The web app's taste-profile endpoints. Both answer quietly for signed-out requests
 * (204 / empty map), and the server itself refuses to store anything while the person has
 * Personalization switched off in Settings.
 */
interface EngagementApi {

    @POST("api/engagement")
    suspend fun record(@Body body: EngagementBody): Response<Unit>

    @GET("api/engagement/interest")
    suspend fun interest(): Response<InterestResponse>
}
