package app.hush.net

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.hush.Prefs
import app.hush.R
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a new Hush release in the background a few times a day, even when the app is
 * closed, and shows one notification when a newer version is out. It sends nothing about you or
 * your phone. Switch it off with "Check for updates automatically" in Settings.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.autoUpdate) return Result.success()
        return try {
            val release = Updater.latest()
            Prefs.markChecked()
            val current = currentVersion(applicationContext)
            if (Updater.isNewer(release.tag, current) && Prefs.notifiedTag() != release.tag) {
                if (notify(applicationContext, release)) Prefs.markNotified(release.tag)
            }
            Result.success()
        } catch (e: Exception) {
            // No signal or GitHub is busy: try again at the next scheduled check.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "hush_update_check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 4242

        fun currentVersion(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

        /** Start (or stop) the background check to match the Settings switch. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, release: Updater.Release): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new version of Hush is available"
                }
            )
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl.ifBlank { release.apkUrl.orEmpty() }))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle("Hush ${release.tag} is available")
                .setContentText("Tap to see what's new and download it.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            return try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
