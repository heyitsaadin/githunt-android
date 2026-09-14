package com.githunt.android.data.api

import com.githunt.android.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface mirroring the Next.js API routes under app/api/ in the
 * GitHunt web repo, 1:1. Every path here corresponds to an existing
 * route.js file, so this app talks to the exact same backend/database as
 * the website rather than a reimplementation.
 *
 * Auth is cookie-based (see PersistentCookieJar) — none of these methods
 * need an explicit token param, matching how the web app's fetch() calls
 * work (credentials carried automatically once signed in).
 */
interface GitHuntApi {

    // ---- Auth ----------------------------------------------------------

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    @PATCH("api/auth/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<AuthResponse>

    @DELETE("api/auth/me")
    suspend fun deleteAccount(): Response<Unit>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(): Response<Unit>

    // ---- Feed / Discover -------------------------------------------------

    @GET("api/feed/default")
    suspend fun defaultFeed(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 20,
    ): Response<DefaultFeedResponse>

    // ---- Posts ----------------------------------------------------------

    @GET("api/posts")
    suspend fun getPosts(): Response<PostsResponse>

    @POST("api/posts")
    suspend fun createPost(@Body body: CreatePostRequest): Response<PostResponse>

    @GET("api/posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Response<PostResponse>

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: Int): Response<Unit>

    @GET("api/posts/{id}/comments")
    suspend fun getComments(@Path("id") id: Int): Response<CommentsResponse>

    @POST("api/posts/{id}/comments")
    suspend fun createComment(@Path("id") id: Int, @Body body: CreateCommentRequest): Response<CommentResponse>

    // ---- Ask AI (Groq) ----------------------------------------------------

    @POST("api/ai-chat")
    suspend fun askAi(@Body body: AiChatRequest): Response<AiChatResponse>

    // ---- Translation ------------------------------------------------------

    @POST("api/translate")
    suspend fun translate(@Body body: TranslateRequest): Response<TranslateResponse>

    // ---- Engagement / personalization --------------------------------------

    @POST("api/engagement")
    suspend fun recordEngagement(@Body body: EngagementEventRequest): Response<Unit>

    @GET("api/build-logs")
    suspend fun buildLogs(): Response<BuildLogsResponse>

    // ---- Repo attach lookup (compose "attach a repo" flow) -----------------

    @GET("api/github/repo")
    suspend fun lookupRepo(@Query("input") input: String): Response<AttachedRepoResponse>
}

@kotlinx.serialization.Serializable
data class BuildLogEntry(
    val version: String,
    val buildName: String? = null,
    val buildNumber: Int? = null,
    val date: String? = null,
    val body: String? = null,
)

@kotlinx.serialization.Serializable
data class BuildLogsResponse(val logs: List<BuildLogEntry> = emptyList(), val error: String? = null)
