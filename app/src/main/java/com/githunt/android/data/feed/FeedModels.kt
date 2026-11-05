package com.githunt.android.data.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * GET /api/feed/default. Each entry in [repos] is a repo object exactly as the
 * website's cards read it (full_name, owner, owner_avatar, stars, ...). They are
 * kept as raw JSON and read by hand so one odd field can never break the page.
 */
@Serializable
data class DefaultFeedResponse(
    val repos: List<JsonElement> = emptyList(),
    val page: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
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
    val updatedAt: String?,
    val readmeSnippet: String?,
    val readmeImages: List<String>,
) : FeedItem {
    override val key: String get() = "repo-$fullName"
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

/** [nextCursor] is the next page number to ask for. */
data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: Long?,
    val hasMore: Boolean,
)

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(key: String): Int =
    text(key)?.toDoubleOrNull()?.toInt() ?: 0

private fun JsonObject.textList(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { el -> (el as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content }
        ?: emptyList()

private fun JsonObject.toRepoItem(): RepoItem? {
    val fullName = text("full_name") ?: return null
    return RepoItem(
        seqId = text("id")?.toLongOrNull() ?: fullName.hashCode().toLong(),
        fullName = fullName,
        owner = text("owner") ?: fullName.substringBefore('/'),
        avatarUrl = text("owner_avatar"),
        description = text("description"),
        url = text("html_url"),
        stars = number("stars"),
        forks = number("forks"),
        language = text("language"),
        topics = textList("topics"),
        updatedAt = text("updated_at"),
        readmeSnippet = text("readme_snippet"),
        readmeImages = textList("readme_images"),
    )
}

fun DefaultFeedResponse.toPage(requestedPage: Int): FeedPage {
    val items = repos.mapNotNull { (it as? JsonObject)?.toRepoItem() }
    return FeedPage(
        items = items,
        nextCursor = (requestedPage + 1).toLong(),
        hasMore = hasMore && items.isNotEmpty(),
    )
}
