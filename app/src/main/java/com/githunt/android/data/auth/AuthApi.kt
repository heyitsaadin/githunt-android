package com.githunt.android.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/** The existing GitHunt web backend. Nothing here needed a server change except /google/mobile. */
interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<UserEnvelope>

    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<UserEnvelope>

    @POST("api/auth/google/mobile")
    suspend fun googleSignIn(@Body body: GoogleTokenRequest): Response<UserEnvelope>

    @GET("api/auth/me")
    suspend fun me(): Response<MeEnvelope>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(): Response<ResendResponse>
}
