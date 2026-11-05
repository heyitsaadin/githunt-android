package com.githunt.android.data.feed

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** The existing GitHunt feed. Public, so it needs no sign-in. */
interface FeedApi {

    /** No cursor = newest page. [before] pages further back (pass the previous next_seq_id). */
    @GET("api/feed")
    suspend fun feed(
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 20,
    ): Response<FeedResponse>
}
