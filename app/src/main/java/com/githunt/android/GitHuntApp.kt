package com.githunt.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.githunt.android.data.profile.DataUriMapper
import com.githunt.android.di.AppContainer

class GitHuntApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Every Coil image in the app can show a photo stored as a `data:` string (the website's format). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(DataUriMapper()) }
            .build()
}
