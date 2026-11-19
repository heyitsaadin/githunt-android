package com.githunt.android.data.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The website's visit tracking endpoint (POST /api/track), the same one its VisitTracker uses. The
 * body is one of four actions: session_start, pageview_enter, pageview_leave and pageview_ping.
 * The server records the request's User-Agent, which for this app starts with "GitHunt-Android/",
 * and that is how the admin console tells app traffic from website traffic.
 */
interface AnalyticsApi {

    @POST("api/track")
    suspend fun track(@Body body: JsonObject): Response<TrackReply>
}

/** pageview_enter answers with the new pageview's id; it is sent back unchanged on leave and ping. */
@Serializable
data class TrackReply(val pageviewId: JsonPrimitive? = null)
