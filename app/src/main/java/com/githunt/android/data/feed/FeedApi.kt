package com.githunt.android.data.feed

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The same feed the website's home page shows (GET /api/feed/default).
 * Public, so it needs no sign-in. It is page-numbered: page 0 is the top of the
 * feed, and each later page goes further down.
 */
interface FeedApi {

    @GET("api/feed/default")
    suspend fun feed(
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20,
    ): Response<DefaultFeedResponse>
}
