package com.githunt.android.data.clips

import com.githunt.android.data.feed.RepoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ClipsResponse(
    val clips: List<ClipRow> = emptyList(),
    val clipped: List<String> = emptyList(),
    /** Only present for GET /api/repos/clips?collection=ID. */
    val collection: CollectionInfo? = null,
)

@Serializable
data class ClipRow(
    @SerialName("repo_full_name") val repoFullName: String = "",
    @SerialName("repo_data") val repoData: JsonElement? = null,
)

@Serializable
data class ClipRequest(
    @SerialName("repo_full_name") val repoFullName: String,
    val repo: JsonElement,
)

// ---- Collections (the website's /clipped page: "Watch Later" + the person's own collections) ----

/** The collection a clips list belongs to. [id] stays a JSON primitive: the database may send it as a number or a string. */
@Serializable
data class CollectionInfo(
    val id: JsonPrimitive? = null,
    val name: String = "",
    @SerialName("is_default") val isDefault: Boolean = false,
)

/** One row of GET /api/repos/collections. [previewRepos] are the newest clipped repo objects (cover art). */
@Serializable
data class CollectionRow(
    val id: JsonPrimitive? = null,
    val name: String = "",
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("repo_count") val repoCount: Int = 0,
    @SerialName("preview_repos") val previewRepos: List<JsonElement> = emptyList(),
)

@Serializable
data class CollectionsResponse(
    val collections: List<CollectionRow> = emptyList(),
)

@Serializable
data class CreateCollectionRequest(
    val name: String,
)

@Serializable
data class CreateCollectionResponse(
    val collection: CollectionRow? = null,
)

/** What the UI draws for one collection. */
data class RepoCollection(
    val id: String,
    val name: String,
    /** "Watch Later". Every account has exactly one and it can't be deleted. */
    val isDefault: Boolean,
    val repoCount: Int,
    /** Owner avatars of the newest repos in it, up to four, for the 2x2 cover. */
    val previewAvatars: List<String?>,
)

/** The repos inside one collection. */
data class CollectionContents(
    val name: String,
    val items: List<RepoItem>,
)
