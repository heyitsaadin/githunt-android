package com.githunt.android.data.clips

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.text
import com.githunt.android.data.feed.toRepoItemOrNull
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    // ---- Collections ----

    /** The person's collections, "Watch Later" first, like the website's Clipped page. */
    suspend fun collections(): ApiResult<List<RepoCollection>> =
        when (val result = apiCall(json) { api.collections() }) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.collections.mapNotNull { it.toCollection() })
            is ApiResult.Err -> result
        }

    /** The repos inside one collection, newest first. */
    suspend fun inCollection(id: String): ApiResult<CollectionContents> =
        when (val result = apiCall(json) { api.inCollection(id) }) {
            is ApiResult.Ok -> ApiResult.Ok(
                CollectionContents(
                    name = result.value.collection?.name.orEmpty(),
                    items = result.value.clips
                        .mapNotNull { it.repoData?.toRepoItemOrNull() }
                        .distinctBy { it.fullName },
                ),
            )
            is ApiResult.Err -> result
        }

    suspend fun createCollection(name: String): ApiResult<RepoCollection> =
        when (val result = apiCall(json) { api.createCollection(CreateCollectionRequest(name.trim())) }) {
            is ApiResult.Ok -> result.value.collection?.toCollection()?.let { ApiResult.Ok(it) }
                ?: ApiResult.Err("GitHunt sent something unexpected. Please try again.", ApiResult.Kind.Server)
            is ApiResult.Err -> result
        }

    suspend fun deleteCollection(id: String): ApiResult<Unit> =
        when (val result = apiCall(json) { api.deleteCollection(id) }) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
}

private fun CollectionRow.toCollection(): RepoCollection? {
    val key = id?.content?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    return RepoCollection(
        id = key,
        name = name,
        isDefault = isDefault,
        repoCount = repoCount,
        previewAvatars = previewRepos.take(4).map { (it as? JsonObject)?.text("owner_avatar") },
    )
}
