package com.githunt.android.data.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * GET /api/feed. Ids come from Postgres bigint and may arrive as numbers OR
 * strings, so they are read as raw JSON and parsed by hand.
 */
@Serializable
data class FeedResponse(
    val items: List<FeedItemDto> = emptyList(),
    @SerialName("next_seq_id") val nextSeqId: JsonElement? = null,
    @SerialName("latest_seq_id") val latestSeqId: JsonElement? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class FeedItemDto(
    val type: String = "",
    @SerialName("seq_id") val seqId: JsonElement? = null,
    val data: JsonElement? = null,
)

/** What the UI draws. */
sealed interface FeedItem {
    val key: String
    val seqId: Long
}

data class RepoItem(
    override val seqId: Long,
    val fullName: String,
    val owner: String,
    val avatarUrl: String?,
    val description: String?,
    val url: String?,
    val stars: Int,
    val forks: Int,
    val language: String?,
    val topics: List<String>,
) : FeedItem {
    override val key: String get() = "repo-$seqId"
}

data class PostItem(
    override val seqId: Long,
    val author: String,
    val avatarUrl: String?,
    val body: String,
    val likes: Int,
    val comments: Int,
) : FeedItem {
    override val key: String get() = "post-$seqId"
}

data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: Long?,
    val hasMore: Boolean,
)

private fun JsonElement?.asLong(): Long? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toLongOrNull()

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(key: String): Int =
    text(key)?.toDoubleOrNull()?.toInt() ?: 0

private fun JsonObject.textList(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { el -> (el as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content }
        ?: emptyList()

private fun FeedItemDto.toItem(): FeedItem? {
    val seq = seqId.asLong() ?: return null
    val obj = data as? JsonObject ?: return null
    return when (type) {
        "repo" -> {
            val fullName = obj.text("full_name") ?: return null
            RepoItem(
                seqId = seq,
                fullName = fullName,
                owner = obj.text("owner") ?: fullName.substringBefore('/'),
                avatarUrl = obj.text("owner_avatar"),
                description = obj.text("description"),
                url = obj.text("html_url"),
                stars = obj.number("stars"),
                forks = obj.number("forks"),
                language = obj.text("language"),
                topics = obj.textList("topics"),
            )
        }
        "post" -> PostItem(
            seqId = seq,
            author = obj.text("author_name") ?: "someone",
            avatarUrl = obj.text("author_avatar"),
            body = obj.text("body") ?: "",
            likes = obj.number("likes"),
            comments = obj.number("comment_count"),
        )
        else -> null
    }
}

fun FeedResponse.toPage(): FeedPage {
    val cursor = nextSeqId.asLong()
    return FeedPage(
        items = items.mapNotNull { it.toItem() },
        nextCursor = cursor,
        hasMore = hasMore && cursor != null,
    )
}
