package com.githunt.android.data.discover

import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.toRepoItemOrNull
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json

/** The same five sorts as the website's Discover page. [param] is what the backend expects. */
enum class SortMode(val param: String, val label: String, val sublabel: String) {
    Trending("hot", "Trending", "Popular repos from the last 30 days"),
    TopRated("top", "Top rated", "Highest star count overall"),
    Recent("new", "Recently added", "Newest repos on GitHub"),
    Active("active", "Most active", "Most recently pushed to"),
    Shared("shared", "Top shared", "Most shared on GitHunt"),
}

/** [nextPage] is the page number to ask for after this one. */
data class RepoPage(
    val items: List<RepoItem>,
    val hasMore: Boolean,
    val nextPage: Int,
)

class DiscoverRepository(
    private val api: DiscoverApi,
    private val json: Json,
) {
    /**
     * Loads one page. [page] starts at 1. Like the website, an empty Trending page that says
     * there is more is skipped over (the server filters spam after fetching), up to five times.
     */
    suspend fun load(
        mode: SortMode,
        language: String,
        query: String,
        page: Int,
        force: Boolean,
    ): ApiResult<RepoPage> {
        val useShared = mode == SortMode.Shared && query.isBlank()
        val lang = language.ifBlank { null }
        val q = query.ifBlank { null }
        val forceFlag = if (force) "1" else null

        var current = page
        for (attempt in 0..5) {
            val result = apiCall(json) {
                if (useShared) {
                    api.shared(current, lang, forceFlag)
                } else {
                    api.browse(current, mode.param, lang, q, forceFlag)
                }
            }
            when (result) {
                is ApiResult.Err -> return result
                is ApiResult.Ok -> {
                    val items = result.value.repos.mapNotNull { it.toRepoItemOrNull() }
                    val hasMore = result.value.hasMore
                    val skip = items.isEmpty() && q == null && mode == SortMode.Trending && hasMore
                    if (!skip) return ApiResult.Ok(RepoPage(items, hasMore, current + 1))
                    current += 1
                }
            }
        }
        return ApiResult.Ok(RepoPage(emptyList(), false, current))
    }
}
