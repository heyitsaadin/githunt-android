package com.githunt.android

import android.app.Application
import com.githunt.android.util.AppLog

class GitHuntApp : Application() {
    override fun onCreate() {
        // AppLog.init() must be the very first thing here, before
        // super.onCreate() even, so nothing that happens during startup
        // (network client construction, cookie jar init, the first
        // /api/auth/me check, etc.) is missed. See util/AppLog.kt for why
        // this exists instead of just using Logcat.
        AppLog.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("Crash", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate()
        // Network client, cookie jar, and repositories are all lazily
        // constructed singletons (see data/api/NetworkModule.kt and
        // data/repo/*), so there's nothing else to eagerly init here.
    }
}
