package com.githunt.android.data.ai

import com.githunt.android.data.discover.DiscoverRepository
import com.githunt.android.data.discover.SortMode
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The website's two AI endpoints.
 *  - POST /api/ai-chat   : "Ask AI" on a repo card (questions about one repo).
 *  - POST /api/ai-search : the Home search box (chat, repo search, or both).
 * Both work signed out too; the server rate-limits them per IP and per user.
 */
interface AiApi {
    @POST("api/ai-chat")
    suspend fun chat(@Body body: AiChatRequest): Response<AiChatResponse>

    @POST("api/ai-search")
    suspend fun search(@Body body: AiSearchRequest): Response<AiSearchResponse>
}

@Serializable
data class AiChatMessage(val role: String, val content: String)

/** The repo facts the model is allowed to use. Same fields the website's RepoAskAI sends. */
@Serializable
data class AiChatRepoContext(
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val stars: Int = 0,
    val forks: Int = 0,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("readme_snippet") val readmeSnippet: String? = null,
)

@Serializable
data class AiChatRequest(
    val message: String,
    val history: List<AiChatMessage> = emptyList(),
    val repo: AiChatRepoContext,
)

@Serializable
data class AiChatResponse(
    val reply: String? = null,
    val error: String? = null,
)

@Serializable
data class AiSearchRequest(
    val query: String,
    val history: List<AiChatMessage> = emptyList(),
)

/** [intent] is CHAT, SEARCH or MIXED. [q] / [language] are only set for SEARCH and MIXED. */
@Serializable
data class AiSearchResponse(
    val intent: String = "SEARCH",
    val q: String = "",
    val language: String = "",
    val reply: String = "",
)

class AiRepository(
    private val api: AiApi,
    private val discover: DiscoverRepository,
    private val json: Json,
) {

    /** One question about [repo]. [history] is the earlier turns only, not [message]. */
    suspend fun ask(repo: RepoItem, history: List<AiChatMessage>, message: String): ApiResult<String> {
        val body = AiChatRequest(
            message = message,
            history = history.takeLast(MAX_HISTORY),
            repo = AiChatRepoContext(
                fullName = repo.fullName,
                description = repo.description,
                language = repo.language,
                topics = repo.topics.take(15),
                stars = repo.stars,
                forks = repo.forks,
                htmlUrl = repo.url,
                readmeSnippet = repo.readmeSnippet,
            ),
        )
        return when (val result = apiCall(json) { api.chat(body) }) {
            is ApiResult.Ok -> {
                val reply = result.value.reply
                if (reply.isNullOrBlank()) {
                    ApiResult.Err("Couldn't get a response.", ApiResult.Kind.Server)
                } else {
                    ApiResult.Ok(reply)
                }
            }
            is ApiResult.Err -> result
        }
    }

    /** Asks the model what a Home search-box message means (chat, search, or both). */
    suspend fun interpret(query: String, history: List<AiChatMessage>): ApiResult<AiSearchResponse> {
        val body = AiSearchRequest(query = query, history = history.takeLast(MAX_SEARCH_HISTORY))
        return apiCall(json) { api.search(body) }
    }

    /**
     * Runs the real GitHub search the model picked terms for. Same endpoint Discover uses, so what
     * comes back is always a live result set, never something the model made up.
     */
    suspend fun searchRepos(q: String, language: String): ApiResult<List<RepoItem>> =
        when (val result = discover.load(SortMode.Trending, language, q, page = 1, force = false)) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.items)
            is ApiResult.Err -> result
        }

    private companion object {
        const val MAX_HISTORY = 12
        const val MAX_SEARCH_HISTORY = 10
    }
}
