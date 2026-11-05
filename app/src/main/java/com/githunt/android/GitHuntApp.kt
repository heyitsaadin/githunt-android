package com.githunt.android

import android.app.Application
import com.githunt.android.di.AppContainer

class GitHuntApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
