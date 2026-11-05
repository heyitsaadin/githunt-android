package com.githunt.android.data.feed

import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json

class FeedRepository(
    private val api: FeedApi,
    private val json: Json,
) {
    suspend fun load(before: Long? = null): ApiResult<FeedPage> =
        when (val result = apiCall(json) { api.feed(before = before) }) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.toPage())
            is ApiResult.Err -> result
        }
}
