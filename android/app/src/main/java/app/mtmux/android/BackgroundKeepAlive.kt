package app.mtmux.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.mtmux.core.data.diag.DiagLog
import app.mtmux.core.model.locale.AppLocale
import app.mtmux.core.ui.R as UiR

/**
 * Keeps the terminal's connection for the user's chosen time after leaving the app. Without
 * a foreground service the system freezes the process and cuts its network soon after.
 */
object BackgroundKeepAlive {
    /** Runs on the main thread when the window ends or the user disconnects from the notification. */
    @Volatile
    var onExpired: (() -> Unit)? = null

    @Volatile
    private var running = false

    /** Call while the activity is still visible: a background app may not start this service. */
    fun enter(context: Context, label: String, minutes: Int) {
        if (minutes <= 0 || running) return
        val intent = Intent(context, BackgroundKeepService::class.java)
            .setAction(BackgroundKeepService.ACTION_START)
            .putExtra(BackgroundKeepService.EXTRA_LABEL, label)
            .putExtra(BackgroundKeepService.EXTRA_MINUTES, minutes)
        // Throws when the system refuses a background start; the link then lasts as long as it can.
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onSuccess {
                running = true
                DiagLog.i("keep", "start ${minutes}min")
            }
            .onFailure { DiagLog.w("keep", "start refused: ${it.message}") }
    }

    fun exit(context: Context) {
        if (!running) return
        running = false
        val intent = Intent(context, BackgroundKeepService::class.java)
            .setAction(BackgroundKeepService.ACTION_STOP)
        // Not stopService: that can overtake a pending start, and a service stopped before
        // it reaches startForeground crashes the app.
        runCatching { context.startService(intent) }
            .onFailure { context.stopService(Intent(context, BackgroundKeepService::class.java)) }
    }

    internal fun onServiceEnded() {
        running = false
    }
}

class BackgroundKeepService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val expire = Runnable { endWindow("window ended") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWindow(
                label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                minutes = intent.getIntExtra(EXTRA_MINUTES, 0).coerceAtLeast(1),
            )
            ACTION_DISCONNECT -> endWindow("disconnected from notification")
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun startWindow(label: String, minutes: Int) {
        val windowMs = minutes * 60_000L
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(label, windowMs),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
        if (started.isFailure) {
            DiagLog.w("keep", "foreground refused: ${started.exceptionOrNull()?.message}")
            finish()
            return
        }
        // SSH keepalives must keep running with the screen off, or NAT and the server drop the link.
        wakeLock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "muxa:session-keep")
            .apply { setReferenceCounted(false) }
        wakeLock?.acquire(windowMs)
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, windowMs)
    }

    private fun endWindow(reason: String) {
        DiagLog.i("keep", "end: $reason")
        BackgroundKeepAlive.onExpired?.invoke()
        finish()
    }

    private fun finish() {
        BackgroundKeepAlive.onServiceEnded()
        handler.removeCallbacks(expire)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(expire)
        wakeLock?.takeIf { it.isHeld }?.release()
        BackgroundKeepAlive.onServiceEnded()
        super.onDestroy()
    }

    private fun buildNotification(label: String, windowMs: Long): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                AppLocale.getString(this, UiR.string.keep_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundKeepService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_keep)
            .setContentTitle(AppLocale.getString(this, UiR.string.keep_notif_title))
            .setContentText(label)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis() + windowMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, AppLocale.getString(this, UiR.string.keep_notif_disconnect), disconnect)
            .build()
    }

    companion object {
        const val ACTION_START = "app.mtmux.android.keep.START"
        const val ACTION_STOP = "app.mtmux.android.keep.STOP"
        const val ACTION_DISCONNECT = "app.mtmux.android.keep.DISCONNECT"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MINUTES = "minutes"
        private const val CHANNEL_ID = "session_keep"
        private const val NOTIFICATION_ID = 41
    }
}
