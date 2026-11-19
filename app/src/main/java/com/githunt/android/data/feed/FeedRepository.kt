package com.githunt.android.data.feed

import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class FeedRepository(
    private val api: FeedApi,
    private val json: Json,
    /** Where the last first page is kept so the next launch can show it before the network answers. */
    private val cacheFile: File? = null,
) {
    /** [page] 0 is the top of the feed; pass the previous page + 1 to go further down. */
    suspend fun load(page: Int = 0): ApiResult<FeedPage> =
        when (val result = apiCall(json) { api.feed(page = page) }) {
            is ApiResult.Ok -> {
                if (page == 0 && result.value.repos.isNotEmpty()) saveFirstPage(result.value)
                ApiResult.Ok(result.value.toPage(page))
            }
            is ApiResult.Err -> result
        }

    /**
     * The first page from the last successful load, or null if there isn't one (first launch,
     * unreadable file). Lets the feed paint straight away on launch while a fresh page loads.
     */
    suspend fun cachedFirstPage(): FeedPage? = withContext(Dispatchers.IO) {
        val file = cacheFile ?: return@withContext null
        runCatching {
            json.decodeFromString(DefaultFeedResponse.serializer(), file.readText()).toPage(0)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    private suspend fun saveFirstPage(response: DefaultFeedResponse) = withContext(Dispatchers.IO) {
        val file = cacheFile ?: return@withContext
        runCatching {
            file.writeText(json.encodeToString(DefaultFeedResponse.serializer(), response))
        }
    }
}
