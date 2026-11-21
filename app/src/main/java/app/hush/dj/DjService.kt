package app.hush.dj

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.hush.HushApp
import app.hush.MainActivity
import app.hush.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the DJ set alive while the screen is off and shows a notification with
 * play/pause, "Mix now" and stop buttons.
 */
class DjService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var noisy: BroadcastReceiver? = null

    private val engine get() = (application as HushApp).dj

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "DJ mode", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                engine.pause()
            }
        }
        noisy = receiver
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.toggle()
            ACTION_MIX -> engine.mixNow(false)
            ACTION_STOP -> {
                engine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!engine.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, ID, build(engine.ui.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        if (watcher == null) {
            watcher = scope.launch {
                engine.ui
                    .map { Triple(it.running, it.playing, it.title + "|" + it.artist + "|" + it.status) }
                    .distinctUntilChanged()
                    .collect { (running, _, _) ->
                        if (!running) {
                            stopSelf()
                        } else {
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(ID, build(engine.ui.value))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun action(name: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, DjService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun build(ui: DjUi): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (ui.title.isNotBlank()) ui.title else "DJ mode"
        val text = if (ui.status.isNotBlank()) ui.status else if (ui.artist.isNotBlank()) ui.artist else "Getting ready"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (ui.playing) "Pause" else "Play", action(ACTION_TOGGLE, 1))
            .addAction(0, "Mix now", action(ACTION_MIX, 2))
            .addAction(0, "Stop", action(ACTION_STOP, 3))
            .build()
    }

    override fun onDestroy() {
        watcher?.cancel()
        noisy?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "hush_dj"
        const val ID = 4242
        const val ACTION_TOGGLE = "app.hush.dj.TOGGLE"
        const val ACTION_MIX = "app.hush.dj.MIX"
        const val ACTION_STOP = "app.hush.dj.STOP"
    }
}
