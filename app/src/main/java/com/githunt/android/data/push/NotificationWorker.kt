package com.githunt.android.data.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.githunt.android.GitHuntApp

/**
 * Runs about every 15 minutes while notifications are on for this phone. It asks
 * the server what's new and shows it; see [DevicePush] for the rules.
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val push = (applicationContext as GitHuntApp).container.devicePush
        if (push.checkNow() == DevicePush.Check.SignedOut) {
            // Nobody is signed in (or the session was revoked). Stop; signing in schedules it again.
            push.stopPolling()
        }
        // A failed check just waits for the next run.
        return Result.success()
    }
}
