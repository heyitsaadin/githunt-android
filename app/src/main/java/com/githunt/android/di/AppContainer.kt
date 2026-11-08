package com.githunt.android.di

import android.content.Context
import android.os.Build
import com.githunt.android.BuildConfig
import com.githunt.android.data.auth.AuthApi
import com.githunt.android.data.auth.AuthRepository
import com.githunt.android.data.auth.GoogleSignIn
import com.githunt.android.data.clips.ClipsApi
import com.githunt.android.data.clips.ClipsRepository
import com.githunt.android.data.discover.DiscoverApi
import com.githunt.android.data.discover.DiscoverRepository
import com.githunt.android.data.net.SessionCookieJar
import com.githunt.android.data.profile.ProfileApi
import com.githunt.android.data.profile.ProfileRepository
import com.githunt.android.data.readme.ReadmeApi
import com.githunt.android.data.readme.ReadmeRepository
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
        // The feed page does live GitHub lookups on the server, so a cold page can take a while.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val settingsRepository = com.githunt.android.data.settings.SettingsRepository(
        retrofit.create(com.githunt.android.data.settings.SettingsApi::class.java),
        json,
    )

    /** Notifications on this phone: registers the device and shows new alerts (see DevicePush). */
    val devicePush = com.githunt.android.data.push.DevicePush(
        context.applicationContext,
        settingsRepository,
        cookieJar,
        okHttp,
    )

    val authRepository = AuthRepository(
        retrofit.create(AuthApi::class.java),
        cookieJar,
        json,
        // Stop notifications for this phone while the session can still authenticate the call.
        beforeLogout = { devicePush.onSignedOut() },
    )

    val googleSignIn = GoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID)

    val feedRepository = com.githunt.android.data.feed.FeedRepository(
        retrofit.create(com.githunt.android.data.feed.FeedApi::class.java),
        json,
    )

    val clipsRepository = ClipsRepository(retrofit.create(ClipsApi::class.java), json)

    val discoverRepository = DiscoverRepository(retrofit.create(DiscoverApi::class.java), json)

    /** Ask AI on repo cards and the AI search box on Home (/api/ai-chat and /api/ai-search). */
    val aiRepository = com.githunt.android.data.ai.AiRepository(
        retrofit.create(com.githunt.android.data.ai.AiApi::class.java),
        discoverRepository,
        json,
    )

    /** Loads README markdown for the repo card's "Show README" button. */
    val readmeRepository = ReadmeRepository(retrofit.create(ReadmeApi::class.java))

    /** Auto-translates repo card text into the language chosen in Settings (/api/translate). */
    val translateRepository = com.githunt.android.data.translate.TranslateRepository(
        retrofit.create(com.githunt.android.data.translate.TranslateApi::class.java),
    )

    val profileRepository = ProfileRepository(retrofit.create(ProfileApi::class.java), clipsRepository, json)

    /** Device-only settings: appearance and translation language. */
    val appPreferences = com.githunt.android.data.settings.AppPreferences(context.applicationContext)
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
