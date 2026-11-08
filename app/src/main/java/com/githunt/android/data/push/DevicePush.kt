package com.githunt.android.data.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.githunt.android.BuildConfig
import com.githunt.android.MainActivity
import com.githunt.android.R
import com.githunt.android.data.net.ApiResult
import com.githunt.android.data.net.SessionCookieJar
import com.githunt.android.data.settings.InboxNotification
import com.githunt.android.data.settings.NotificationInboxResponse
import com.githunt.android.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.Instant
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Notifications on this phone, without Firebase.
 *
 * The server can only push to browsers (Web Push), so this phone registers as a
 * "pull device": the server saves every alert it would have pushed in the 24h
 * inbox, and a background job ([NotificationWorker]) fetches the inbox about every
 * 15 minutes and shows what's new as system notifications. All the rules (which
 * alerts, quiet hours, interests, never the same repo twice) are applied by the
 * server when it creates the alert, exactly as for the website.
 *
 * Two things the server can't decide for an inbox row, so they're applied here
 * using what the server reports with each poll: "what's new" announcements are
 * saved for everyone, so they respect "alerts off" and quiet hours here; and
 * anything older than 6 hours is dropped, the same lifetime a web push has.
 */
class DevicePush(
    context: Context,
    private val repo: SettingsRepository,
    private val cookieJar: SessionCookieJar,
    private val http: OkHttpClient,
) {

    enum class Check { Done, SignedOut, Failed }

    private val app = context.applicationContext
    private val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** The person's choice for this phone. Survives sign-out so signing back in restores it. */
    private val _enabled = MutableStateFlow(store.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // ---- permission ----------------------------------------------------------

    /** Android 13+ asks for notification permission at runtime. */
    fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** False when the permission is missing or notifications are turned off for GitHunt in system settings. */
    fun canShow(): Boolean = !needsPermission() && NotificationManagerCompat.from(app).areNotificationsEnabled()

    // ---- first-run prompt ----------------------------------------------------

    fun shouldAutoEnable(): Boolean =
        !_enabled.value && !store.getBoolean(KEY_OPTED_OUT, false) && !store.getBoolean(KEY_PROMPTED, false)

    fun markPrompted() {
        store.edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    // ---- turning it on / off -------------------------------------------------

    /** Registers this phone with the server, starts the background check, remembers the choice. */
    suspend fun enable(): ApiResult<Unit> = lock.withLock {
        ensureChannels()
        val id = deviceId()
        when (val result = repo.registerDevice(id, tzOffsetMin())) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> {
                baseline(id)
                store.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_OPTED_OUT, false).apply()
                _enabled.value = true
                schedule()
                ApiResult.Ok(Unit)
            }
        }
    }

    /** The person turned it off: stop checking and tell the server. Never auto-enabled again. */
    suspend fun disable() {
        lock.withLock {
            store.edit().putBoolean(KEY_ENABLED, false).putBoolean(KEY_OPTED_OUT, true).apply()
            _enabled.value = false
            cancelWork()
            if (cookieJar.hasSession()) repo.unregisterDevice(deviceId())
        }
    }

    /** Called whenever someone is signed in at app start: keeps the server and the schedule in step. */
    suspend fun onSignedIn() {
        if (!_enabled.value) return
        lock.withLock {
            ensureChannels()
            val id = deviceId()
            // Idempotent. If it fails (offline) the next launch or the 'unknown device' reply retries.
            repo.registerDevice(id, tzOffsetMin())
            if (store.getString(KEY_BASELINE, null) == null) baseline(id)
            schedule()
        }
    }

    /** Called just before logout, while the session still works. The choice itself is kept. */
    suspend fun onSignedOut() {
        lock.withLock {
            cancelWork()
            if (_enabled.value && cookieJar.hasSession()) repo.unregisterDevice(deviceId())
            // The next account starts from a clean slate, with no burst of someone else's alerts.
            store.edit().remove(KEY_BASELINE).remove(KEY_NOTIFIED).apply()
        }
    }

    fun stopPolling() = cancelWork()

    // ---- checking for new alerts ---------------------------------------------

    /** One round: fetch the inbox and show whatever is new. Used by the worker and by "Send test". */
    suspend fun checkNow(): Check = lock.withLock {
        if (!_enabled.value) return@withLock Check.Done
        if (!cookieJar.hasSession()) return@withLock Check.SignedOut
        val id = deviceId()
        when (val result = repo.pollNotifications(id)) {
            is ApiResult.Err ->
                if (result.kind == ApiResult.Kind.Unauthorized) Check.SignedOut else Check.Failed
            is ApiResult.Ok -> {
                handle(id, result.value)
                Check.Done
            }
        }
    }

    private suspend fun handle(id: String, resp: NotificationInboxResponse) {
        if (resp.device == "unknown") repo.registerDevice(id, tzOffsetMin())

        val baseline = store.getString(KEY_BASELINE, null)?.let { parse(it) }
        if (baseline == null) {
            // First look (or cleared data): remember where we are, don't replay the backlog.
            val newest = resp.notifications.firstOrNull()?.createdAt?.let { parse(it) }
            store.edit().putString(KEY_BASELINE, (newest ?: Instant.now().minusSeconds(120)).toString()).apply()
            return
        }

        val notified = store.getString(KEY_NOTIFIED, "").orEmpty().split(',').filter { it.isNotEmpty() }.toMutableList()
        val now = Instant.now()
        val show = mutableListOf<InboxNotification>() // newest first, like the server list
        var changed = false

        for (item in resp.notifications) {
            if (item.id in notified) continue
            val at = parse(item.createdAt) ?: continue
            if (!at.isAfter(baseline)) continue
            val done = when {
                Duration.between(at, now) > MAX_AGE -> true
                resp.deliver?.enabled == false -> true
                // Announcements are saved for everyone; hold them until quiet hours end (or they expire).
                item.kind == "announce" && resp.deliver?.quiet == true -> false
                else -> {
                    show.add(item)
                    true
                }
            }
            if (done) {
                notified.add(item.id)
                changed = true
            }
        }

        // Oldest first so the newest ends up on top of the shade.
        show.take(MAX_SHOWN).reversed().forEach { post(it) }

        if (changed) {
            while (notified.size > MAX_REMEMBERED) notified.removeAt(0)
            store.edit().putString(KEY_NOTIFIED, notified.joinToString(",")).apply()
        }
    }

    /** Sets the "nothing before this counts" mark to the newest alert already on the server. */
    private suspend fun baseline(id: String) {
        val newest = (repo.pollNotifications(id) as? ApiResult.Ok)
            ?.value?.notifications?.firstOrNull()?.createdAt?.let { parse(it) }
        store.edit()
            .putString(KEY_BASELINE, (newest ?: Instant.now().minusSeconds(120)).toString())
            .remove(KEY_NOTIFIED)
            .apply()
    }

    // ---- showing a notification ----------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun post(item: InboxNotification) {
        if (!canShow()) return
        ensureChannels()

        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, OPEN_NOTIFICATIONS)
        }
        val notificationId = item.id.toIntOrNull() ?: item.id.hashCode()
        val tap = PendingIntent.getActivity(
            app,
            notificationId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = item.body.trim()
        val builder = NotificationCompat.Builder(app, channelFor(item.kind))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(item.title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)

        val picture = item.image?.let { loadPicture(it) }
        if (picture != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).setSummaryText(text))
        } else if (text.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        NotificationManagerCompat.from(app).notify(notificationId, builder.build())
    }

    /** The repo card image (the server stores it as /api/img-proxy?...). Best effort: no picture is fine. */
    private suspend fun loadPicture(path: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!path.startsWith("/")) return@withContext null
        try {
            val client = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
            val request = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                val bytes = body.bytes()
                if (bytes.size > MAX_IMAGE_BYTES) return@use null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1024) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun channelFor(kind: String): String = when (kind) {
        "hot", "test" -> CH_TRENDING
        "digest" -> CH_DIGEST
        else -> CH_UPDATES
    }

    private fun ensureChannels() {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CH_TRENDING, "Trending alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A repo that is taking off right now"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_DIGEST, "Daily top repos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your daily roundup of the best new repos"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "GitHunt updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "What's new in GitHunt"
            }
        )
    }

    // ---- scheduling and small helpers ----------------------------------------

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelWork() {
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
    }

    /** A random id for this install; the server turns it into the device's address. */
    private fun deviceId(): String {
        store.getString(KEY_DEVICE, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        store.edit().putString(KEY_DEVICE, fresh).apply()
        return fresh
    }

    private fun tzOffsetMin(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    private fun parse(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Intent extra set on a notification tap; MainActivity opens the Notifications page for it. */
        const val EXTRA_OPEN = "githunt_open"
        const val OPEN_NOTIFICATIONS = "notifications"

        private const val PREFS = "githunt_push"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_OPTED_OUT = "opted_out"
        private const val KEY_PROMPTED = "prompted"
        private const val KEY_BASELINE = "baseline"
        private const val KEY_NOTIFIED = "notified"

        private const val WORK_NAME = "githunt-notifications"
        private const val GROUP = "githunt"
        private const val CH_TRENDING = "trending"
        private const val CH_DIGEST = "digest"
        private const val CH_UPDATES = "updates"

        private const val MAX_SHOWN = 5
        private const val MAX_REMEMBERED = 200
        private const val MAX_IMAGE_BYTES = 4_000_000

        // Same lifetime as a web push (the server sends them with a 6 hour TTL).
        private val MAX_AGE: Duration = Duration.ofHours(6)
    }
}
