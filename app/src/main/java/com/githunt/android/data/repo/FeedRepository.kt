package com.githunt.android.data.repo

import android.content.Context
import com.githunt.android.data.api.NetworkModule
import com.githunt.android.data.model.*

class FeedRepository(context: Context) {
    private val api = NetworkModule.api(context)

    suspend fun loadDiscoverPage(page: Int, limit: Int = 20): ApiResult<DefaultFeedResponse> {
        return try {
            val res = api.defaultFeed(page, limit)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Failure(res.body()?.error ?: "Could not load the feed.")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun getPosts(): ApiResult<List<Post>> {
        return try {
            val res = api.getPosts()
            if (res.isSuccessful) ApiResult.Success(res.body()?.posts ?: emptyList())
            else ApiResult.Failure("Could not load posts.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun createPost(request: CreatePostRequest): ApiResult<Post> {
        return try {
            val res = api.createPost(request)
            val body = res.body()
            if (res.isSuccessful && body?.post != null) {
                ApiResult.Success(body.post)
            } else {
                ApiResult.Failure(body?.error ?: "Could not create post.", body?.code, res.code())
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun deletePost(id: Int): ApiResult<Unit> {
        return try {
            val res = api.deletePost(id)
            if (res.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Could not delete post.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun getComments(postId: Int): ApiResult<List<Comment>> {
        return try {
            val res = api.getComments(postId)
            if (res.isSuccessful) ApiResult.Success(res.body()?.comments ?: emptyList())
            else ApiResult.Failure("Could not load replies.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun createComment(postId: Int, text: String, parentId: Int? = null): ApiResult<Comment> {
        return try {
            val res = api.createComment(postId, CreateCommentRequest(text, parentId))
            val body = res.body()
            if (res.isSuccessful && body?.comment != null) ApiResult.Success(body.comment)
            else ApiResult.Failure(body?.error ?: "Could not post reply.", body?.code, res.code())
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun askAi(message: String, repo: RepoAiContext?, history: List<AiChatTurn>): ApiResult<String> {
        return try {
            val res = api.askAi(AiChatRequest(message, repo, history))
            val body = res.body()
            if (res.isSuccessful && body?.reply != null) ApiResult.Success(body.reply)
            else ApiResult.Failure(body?.error ?: "Couldn't get a response. Try again.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun lookupRepo(input: String): ApiResult<AttachedRepo> {
        return try {
            val res = api.lookupRepo(input)
            val body = res.body()
            if (res.isSuccessful && body?.repo != null) ApiResult.Success(body.repo)
            else ApiResult.Failure(body?.error ?: "Couldn't find that repo.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun recordEngagement(event: EngagementEventRequest) {
        try {
            api.recordEngagement(event)
        } catch (_: Exception) {
            // Best-effort, matches the web client's fire-and-forget behavior.
        }
    }

    companion object {
        @Volatile private var instance: FeedRepository? = null
        fun getInstance(context: Context): FeedRepository =
            instance ?: synchronized(this) {
                instance ?: FeedRepository(context.applicationContext).also { instance = it }
            }
    }
}
