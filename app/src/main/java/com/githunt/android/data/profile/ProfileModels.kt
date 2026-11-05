package com.githunt.android.data.profile

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.number
import com.githunt.android.data.feed.text
import com.githunt.android.data.feed.toRepoItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class PostsResponse(val posts: List<JsonElement> = emptyList())

@Serializable
data class FollowingFeedResponse(val items: List<JsonElement> = emptyList())

@Serializable
data class UserReposResponse(val repos: List<JsonElement> = emptyList())

data class ProfilePost(
    val id: Long,
    val userId: Long,
    val author: String,
    val avatarUrl: String?,
    val body: String,
    val imageUrl: String?,
    val likes: Int,
    val comments: Int,
    val createdAt: String?,
)

/** One row of the Following tab: either a post or a repo. */
sealed interface FollowingItem {
    val key: String
}

data class FollowingPost(val post: ProfilePost) : FollowingItem {
    override val key: String get() = "post-${post.id}"
}

data class FollowingRepo(val repo: RepoItem) : FollowingItem {
    override val key: String get() = "repo-${repo.fullName}"
}

internal fun JsonObject.toProfilePost(): ProfilePost? {
    val id = text("id")?.toLongOrNull() ?: return null
    return ProfilePost(
        id = id,
        userId = text("user_id")?.toLongOrNull() ?: -1L,
        author = text("author_name") ?: "GitHunt user",
        avatarUrl = text("author_avatar"),
        body = text("body").orEmpty(),
        imageUrl = text("image_data")?.takeIf { it.startsWith("http") },
        likes = number("likes"),
        comments = number("comment_count"),
        createdAt = text("created_at"),
    )
}

internal fun JsonElement.toFollowingItem(): FollowingItem? {
    val obj = this as? JsonObject ?: return null
    val data = obj["data"] as? JsonObject ?: return null
    return when (obj.text("type")) {
        "post" -> data.toProfilePost()?.let { FollowingPost(it) }
        "repo" -> data.toRepoItem()?.let { FollowingRepo(it) }
        else -> null
    }
}
