package com.githunt.android

import android.app.Application

class GitHuntApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Network client, cookie jar, and repositories are all lazily
        // constructed singletons (see data/api/NetworkModule.kt and
        // data/repo/*), so there's nothing to eagerly init here yet.
    }
}
