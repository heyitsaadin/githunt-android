package com.githunt.android.data.clips

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Private "clipped" repos (the bookmark on every repo card). Needs the signed-in session cookie. */
interface ClipsApi {

    /** Every repo the person has clipped, newest first. */
    @GET("api/repos/clips")
    suspend fun list(): Response<ClipsResponse>

    /** Which of these repos (comma separated full names) are clipped. */
    @GET("api/repos/clips")
    suspend fun among(@Query("repos") repos: String): Response<ClipsResponse>

    @POST("api/repos/clips")
    suspend fun add(@Body body: ClipRequest): Response<Unit>

    @DELETE("api/repos/clips")
    suspend fun remove(@Query("repo") repo: String): Response<Unit>
}
