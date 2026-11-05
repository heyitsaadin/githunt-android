package com.githunt.android.data.profile

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** What the website's own profile page reads. */
interface ProfileApi {

    /** The 100 newest posts from everyone; the profile keeps the ones that are the person's. */
    @GET("api/posts")
    suspend fun posts(): Response<PostsResponse>

    /** Posts from people they follow, plus repos from GitHub accounts they follow. */
    @GET("api/follows/following-feed")
    suspend fun followingFeed(): Response<FollowingFeedResponse>

    /** Repos of the GitHub account connected to the profile. */
    @GET("api/users/{id}/repos")
    suspend fun userRepos(@Path("id") id: Long): Response<UserReposResponse>
}
