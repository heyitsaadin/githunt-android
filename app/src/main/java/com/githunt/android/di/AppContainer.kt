package com.githunt.android.di

import android.content.Context
import android.os.Build
import com.githunt.android.BuildConfig
import com.githunt.android.data.auth.AuthApi
import com.githunt.android.data.auth.AuthRepository
import com.githunt.android.data.auth.GoogleSignIn
import com.githunt.android.data.net.SessionCookieJar
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the app's long-lived objects once. Plain constructor wiring on purpose:
 * no DI framework means fewer things that can break a CI build.
 */
class AppContainer(context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val cookieJar = SessionCookieJar(context.applicationContext)

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(UserAgentInterceptor(userAgent()))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // BASIC logs method, URL and status only. Never bodies: they hold passwords.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }
        )
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authRepository = AuthRepository(retrofit.create(AuthApi::class.java), cookieJar, json)

    val googleSignIn = GoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID)
}

private fun userAgent(): String {
    val raw = "GitHunt-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
    // HTTP headers must be plain ASCII.
    return raw.filter { it.code in 32..126 }
}

private class UserAgentInterceptor(private val value: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", value).build())
}
