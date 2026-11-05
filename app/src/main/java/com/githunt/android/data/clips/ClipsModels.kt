package com.githunt.android.data.clips

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClipsResponse(
    val clips: List<ClipRow> = emptyList(),
    val clipped: List<String> = emptyList(),
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
