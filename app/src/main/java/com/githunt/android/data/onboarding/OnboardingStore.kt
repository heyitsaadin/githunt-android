package com.githunt.android.data.onboarding

import android.content.Context

/**
 * Remembers, per account, that the first-run setup screen (favourite languages, then a few repos to
 * react to) was finished or skipped. Device-only, like [com.githunt.android.data.settings.AppPreferences].
 */
class OnboardingStore(context: Context) {
    private val store = context.getSharedPreferences("githunt_onboarding", Context.MODE_PRIVATE)

    fun isDone(userId: Long): Boolean = store.getBoolean("done_$userId", false)

    fun markDone(userId: Long) {
        store.edit().putBoolean("done_$userId", true).apply()
    }
}
