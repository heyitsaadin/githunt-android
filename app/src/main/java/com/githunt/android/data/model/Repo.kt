package com.githunt.android.data.model

import kotlinx.serialization.Serializable

/**
 * Shape of a repo object as returned by /api/feed/default, /api/github, and
 * embedded as `repo_data` on posts. Field names mirror lib/github.js's
 * shapeRepo() output plus the trend-scan cron's extra `_trend_*` fields.
 */
@Serializable
data class Repo(
    val id: Long? = null,
    val name: String,
    val full_name: String,
    val description: String? = null,
    val html_url: String,
    val language: String? = null,
    val stargazers_count: Int = 0,
    val forks_count: Int = 0,
    val topics: List<String> = emptyList(),
    val owner_login: String? = null,
    val owner_avatar_url: String? = null,
    val readme_snippet: String? = null,
    val fork: Boolean = false,
    // Trend-layer metadata; null for repos surfaced by the live GitHub
    // fallback rather than the trend-scan cron (see feed/default/route.js).
    val _trend_tag: String? = null,
    val _trend_score: Double? = null,
    val _trend_velocity: Double? = null,
    val _stars_snapshot: Int? = null,
)

@Serializable
data class DefaultFeedResponse(
    val repos: List<Repo> = emptyList(),
    val page: Int = 0,
    val has_more: Boolean = true,
    val error: String? = null,
)

@Serializable
data class AiChatRequest(
    val message: String,
    val repo: RepoAiContext? = null,
    val history: List<AiChatTurn> = emptyList(),
)

@Serializable
data class RepoAiContext(
    val full_name: String? = null,
    val description: String? = null,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val stars: Int? = null,
    val forks: Int? = null,
    val html_url: String? = null,
    val readme_snippet: String? = null,
)

@Serializable
data class AiChatTurn(val role: String, val content: String)

@Serializable
data class AiChatResponse(val reply: String? = null, val error: String? = null)

@Serializable
data class TranslateRequest(val texts: List<String>, val target: String = "en")

@Serializable
data class TranslateResponse(
    val translated: List<String> = emptyList(),
    val sameLanguage: List<Boolean> = emptyList(),
    val partial: Boolean = false,
    val error: String? = null,
)

@Serializable
data class EngagementEventRequest(
    val repo_full_name: String,
    val event_type: String,
    val language: String? = null,
    val topics: List<String> = emptyList(),
)
