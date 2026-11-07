package com.githunt.android.data.settings

import com.githunt.android.data.auth.UserEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** The settings endpoints of the existing GitHunt web backend. No server changes needed. */
interface SettingsApi {

    // Security
    @GET("api/auth/sessions")
    suspend fun sessions(): Response<SessionsResponse>

    @DELETE("api/auth/sessions")
    suspend fun revokeSession(@Query("id") id: String): Response<RevokeResponse>

    @DELETE("api/auth/sessions")
    suspend fun revokeOthers(@Query("all_others") flag: Int = 1): Response<Unit>

    // Account
    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    /**
     * The same call the website's edit-profile page makes. Only the keys present in [body] change:
     * display_name, username, bio, avatar_url, background_url (a data URI, or "" to remove), and
     * github_username (only "" is accepted, which disconnects GitHub).
     */
    @PATCH("api/auth/me")
    suspend fun updateProfile(@Body body: JsonObject): Response<UserEnvelope>

    /** `{ "available": true }` when nobody else has [username]; the person's own name counts as free. */
    @GET("api/auth/username-available")
    suspend fun usernameAvailable(@Query("username") username: String): Response<UsernameAvailability>

    @DELETE("api/auth/me")
    suspend fun deleteAccount(): Response<Unit>

    // Personalization
    @GET("api/personalization")
    suspend fun personalization(): Response<PersonalizationState>

    @PATCH("api/personalization")
    suspend fun setPersonalization(@Body body: PersonalizationPatch): Response<Unit>

    @DELETE("api/personalization")
    suspend fun clearPersonalization(): Response<Unit>

    // Notifications
    @GET("api/push/prefs")
    suspend fun pushPrefs(): Response<PushPrefsResponse>

    @PATCH("api/push/prefs")
    suspend fun setHotAlerts(@Body body: HotAlertsPatch): Response<Unit>

    @PATCH("api/push/prefs")
    suspend fun setDailyDigest(@Body body: DigestPatch): Response<Unit>

    @PATCH("api/push/prefs")
    suspend fun setInterests(@Body body: InterestsPatch): Response<InterestsResponse>

    @POST("api/push/test")
    suspend fun pushTest(): Response<Unit>

    // GitHub API connection
    @GET("api/settings/github-token")
    suspend fun githubToken(): Response<GithubTokenStatus>

    @POST("api/settings/github-token")
    suspend fun saveGithubToken(@Body body: SaveTokenRequest): Response<GithubTokenStatus>

    @DELETE("api/settings/github-token")
    suspend fun removeGithubToken(): Response<Unit>

    @GET("api/notifications")
    suspend fun notifications(): Response<NotificationInboxResponse>

    @POST("api/notifications")
    suspend fun markNotificationsSeen(@Body body: NotificationSeenRequest): Response<Unit>

    // About
    @POST("api/feature-requests")
    suspend fun featureRequest(@Body body: FeatureRequestBody): Response<Unit>

    @GET("api/build-logs")
    suspend fun buildLogs(): Response<BuildLogsResponse>
}
