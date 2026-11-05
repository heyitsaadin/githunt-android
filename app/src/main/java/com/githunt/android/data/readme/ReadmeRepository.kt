package com.githunt.android.data.readme

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** The website's README endpoint: GET /api/github/readme?owner=&repo= answers { "markdown": "..." }. */
interface ReadmeApi {
    @GET("api/github/readme")
    suspend fun readme(
        @Query("owner") owner: String,
        @Query("repo") repo: String,
    ): Response<ReadmeResponse>
}

@Serializable
data class ReadmeResponse(
    val markdown: String? = null,
    val error: String? = null,
)

/** Loads README markdown and keeps the last few in memory so reopening a card is instant. */
class ReadmeRepository(private val api: ReadmeApi) {

    private val cache = LruCache<String, String>(40)

    suspend fun load(owner: String, repo: String): Result<String> {
        val key = "$owner/$repo"
        cache.get(key)?.let { return Result.success(it) }
        return try {
            val response = api.readme(owner, repo)
            val markdown = response.body()?.markdown
            if (response.isSuccessful && !markdown.isNullOrBlank()) {
                cache.put(key, markdown)
                Result.success(markdown)
            } else if (response.code() == 404) {
                Result.failure(Exception("No README found for this repo."))
            } else {
                Result.failure(Exception("Could not load the README."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("Could not load the README. Check your connection."))
        }
    }
}
