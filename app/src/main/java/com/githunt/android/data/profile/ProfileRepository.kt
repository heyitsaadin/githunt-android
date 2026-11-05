package com.githunt.android.data.profile

import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.feed.RepoItem
import com.githunt.android.data.feed.toRepoItemOrNull
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.apiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ProfileRepository(
    private val api: ProfileApi,
    private val clips: ClipsRepository,
    private val json: Json,
) {

    /** Posts by [userId]. Like the website, this looks inside the newest 100 posts overall. */
    suspend fun posts(userId: Long): ApiResult<List<ProfilePost>> =
        when (val result = apiCall(json) { api.posts() }) {
            is ApiResult.Ok -> ApiResult.Ok(
                result.value.posts
                    .mapNotNull { (it as? JsonObject)?.toProfilePost() }
                    .filter { it.userId == userId },
            )
            is ApiResult.Err -> result
        }

    suspend fun following(): ApiResult<List<FollowingItem>> =
        when (val result = apiCall(json) { api.followingFeed() }) {
            is ApiResult.Ok -> ApiResult.Ok(
                result.value.items.mapNotNull { it.toFollowingItem() }.distinctBy { it.key },
            )
            is ApiResult.Err -> result
        }

    suspend fun repos(userId: Long): ApiResult<List<RepoItem>> =
        when (val result = apiCall(json) { api.userRepos(userId) }) {
            is ApiResult.Ok -> ApiResult.Ok(
                result.value.repos.mapNotNull { it.toRepoItemOrNull() }.distinctBy { it.fullName },
            )
            is ApiResult.Err -> result
        }

    suspend fun clipped(): ApiResult<List<RepoItem>> = clips.all()
}
