package com.githunt.android.data.discover

import com.githunt.android.data.feed.DefaultFeedResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The website's Discover page endpoints. Pages are 1-based here (unlike /api/feed/default).
 * Null query values are left out of the URL.
 */
interface DiscoverApi {

    @GET("api/github")
    suspend fun browse(
        @Query("page") page: Int,
        @Query("mode") mode: String,
        @Query("language") language: String?,
        @Query("q") query: String?,
        @Query("force") force: String?,
    ): Response<DefaultFeedResponse>

    /** "Top shared": ranked by shares inside GitHunt, not by a GitHub search. */
    @GET("api/github/shared")
    suspend fun shared(
        @Query("page") page: Int,
        @Query("language") language: String?,
        @Query("force") force: String?,
    ): Response<DefaultFeedResponse>
}
