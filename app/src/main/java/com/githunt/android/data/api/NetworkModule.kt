package com.githunt.android.data.api

import android.content.Context
import com.githunt.android.BuildConfig
import com.githunt.android.util.AppLog
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the single shared Retrofit/OkHttp instance for the app. Kept as a
 * simple object rather than a DI framework to keep the project easy to open
 * and read in Android Studio without extra setup — swap in Hilt/Koin later
 * if the app grows.
 */
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var cookieJarRef: PersistentCookieJar? = null

    fun api(context: Context): GitHuntApi = retrofitInstance(context).create(GitHuntApi::class.java)

    /** Exposed so logout / delete-account flows can wipe the session cookie. */
    fun cookieJar(context: Context): PersistentCookieJar {
        return cookieJarRef ?: synchronized(this) {
            cookieJarRef ?: PersistentCookieJar(context).also { cookieJarRef = it }
        }
    }

    private fun retrofitInstance(context: Context): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: build(context).also { retrofit = it }
        }
    }

    private fun build(context: Context): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar(context))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
