package com.githunt.android.data.clips

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.toRepoItemOrNull
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClipsRepository(
    private val api: ClipsApi,
    private val json: Json,
) {

    /** Which of [names] the signed-in person has clipped. */
    suspend fun clippedAmong(names: List<String>): ApiResult<Set<String>> {
        if (names.isEmpty()) return ApiResult.Ok(emptySet())
        return when (val result = apiCall(json) { api.among(names.take(100).joinToString(",")) }) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.clipped.toSet())
            is ApiResult.Err -> result
        }
    }

    /** Everything the person has clipped, as repo cards. */
    suspend fun all(): ApiResult<List<RepoItem>> =
        when (val result = apiCall(json) { api.list() }) {
            is ApiResult.Ok -> ApiResult.Ok(
                result.value.clips
                    .mapNotNull { it.repoData?.toRepoItemOrNull() }
                    .distinctBy { it.fullName },
            )
            is ApiResult.Err -> result
        }

    suspend fun add(item: RepoItem): ApiResult<Unit> {
        val repo: JsonElement = item.raw ?: buildJsonObject {
            put("full_name", item.fullName)
            put("owner", item.owner)
            item.avatarUrl?.let { put("owner_avatar", it) }
            item.description?.let { put("description", it) }
            item.url?.let { put("html_url", it) }
            put("stars", item.stars)
            put("forks", item.forks)
            item.language?.let { put("language", it) }
            put("topics", JsonArray(item.topics.map { JsonPrimitive(it) }))
            item.updatedAt?.let { put("updated_at", it) }
        }
        return when (val result = apiCall(json) { api.add(ClipRequest(item.fullName, repo)) }) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun remove(fullName: String): ApiResult<Unit> =
        when (val result = apiCall(json) { api.remove(fullName) }) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
}
