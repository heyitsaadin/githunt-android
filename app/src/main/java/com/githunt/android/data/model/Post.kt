package com.githunt.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Mirrors the row shape returned by GET/POST /api/posts. */
@Serializable
data class Post(
    val id: Int,
    val user_id: Int? = null,
    val author_name: String? = null,
    val author_avatar: String? = null,
    val body: String? = null,
    val image_data: String? = null,
    val file_name: String? = null,
    val file_size: Long? = null,
    val link_url: String? = null,
    val link_domain: String? = null,
    val repo_data: JsonElement? = null,
    val likes: Int = 0,
    val reposts: Int = 0,
    val comment_count: Int = 0,
    val created_at: String,
)

@Serializable
data class PostsResponse(val posts: List<Post> = emptyList())

@Serializable
data class PostResponse(val post: Post? = null, val error: String? = null, val code: String? = null)

@Serializable
data class ImageAttachment(val dataUrl: String, val name: String)

@Serializable
data class FileAttachment(val name: String, val size: Long)

@Serializable
data class LinkAttachment(val url: String, val domain: String)

/**
 * Shape of the "repo" attachment sent to POST /api/posts, matching exactly
 * what app/compose/page.js puts in state after GET /api/github/repo — note
 * these field names (owner, stars, forks, owner_avatar) differ from the
 * Discover feed's Repo model (owner_login, stargazers_count, etc.); the two
 * endpoints intentionally use different shapes, so this is a distinct type
 * rather than reusing data.model.Repo.
 */
@Serializable
data class AttachedRepo(
    val id: Long? = null,
    val name: String,
    val full_name: String,
    val owner: String? = null,
    val owner_avatar: String? = null,
    val description: String? = null,
    val html_url: String,
    val homepage: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val updated_at: String? = null,
    val created_at: String? = null,
    val default_branch: String? = null,
    val readme_snippet: String? = null,
    val readme_images: List<String> = emptyList(),
)

@Serializable
data class AttachedRepoResponse(val repo: AttachedRepo? = null, val error: String? = null)

@Serializable
data class CreatePostRequest(
    val text: String = "",
    val image: ImageAttachment? = null,
    val file: FileAttachment? = null,
    val link: LinkAttachment? = null,
    val repo: AttachedRepo? = null,
)

@Serializable
data class Comment(
    val id: Int,
    val post_id: Int,
    val parent_id: Int? = null,
    val user_id: Int? = null,
    val author_name: String? = null,
    val author_avatar: String? = null,
    val body: String,
    val likes: Int = 0,
    val created_at: String,
)

@Serializable
data class CommentsResponse(val comments: List<Comment> = emptyList())

@Serializable
data class CommentResponse(val comment: Comment? = null, val error: String? = null, val code: String? = null)

@Serializable
data class CreateCommentRequest(val text: String, val parentId: Int? = null)
