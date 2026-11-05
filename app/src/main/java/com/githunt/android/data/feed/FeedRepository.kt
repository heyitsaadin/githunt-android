package com.githunt.android.data.feed

import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json

class FeedRepository(
    private val api: FeedApi,
    private val json: Json,
) {
    /** [page] 0 is the top of the feed; pass the previous page + 1 to go further down. */
    suspend fun load(page: Int = 0): ApiResult<FeedPage> =
        when (val result = apiCall(json) { api.feed(page = page) }) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.toPage(page))
            is ApiResult.Err -> result
        }
}
