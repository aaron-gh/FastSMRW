package me.masonasons.fastsm.background

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.masonasons.fastsm.FastSmApp
import me.masonasons.fastsm.MainActivity
import me.masonasons.fastsm.R
import me.masonasons.fastsm.core.FastSmCore

/**
 * "Run in the background": a foreground service with a persistent notification,
 * which keeps the process -- and the core session FastSmApp owns -- alive after
 * the window closes, so timelines keep streaming, sounds keep playing and, with
 * "Read new posts in the background" on, auto-read posts are spoken with
 * text-to-speech. Started and stopped by [sync] to match the setting.
 */
class BackgroundService : Service() {

    private var speech: BackgroundSpeech? = null
    private var speechEngine: String? = null
    private var sandfly: SandflySpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val listener = FastSmCore.EventListener { e ->
        if (!FastSmApp.isAutoRead(e)) return@EventListener
        // On screen, the screen reader reads them (MainActivity); off screen, we do.
        if (FastSmApp.of(this).inForeground) return@EventListener
        if (!BackgroundPrefs.readInBackground(this)) return@EventListener
        speakInBackground(e.optString("message"))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val app = FastSmApp.of(this)
        app.addCoreListener(listener)
        // Android restarted the service after killing the process, with no window:
        // start the session ourselves so it carries on.
        if (!app.started) {
            app.started = true
            app.core.dispatch("start")
        }
        applySettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // The notification's Stop: turn the setting off, and if no window is
            // open, let the core go idle as it does without background mode.
            BackgroundPrefs.setRunInBackground(this, false)
            val app = FastSmApp.of(this)
            if (!app.inForeground && app.started) app.core.dispatch("pause")
            stopSelf()
            return START_NOT_STICKY
        }
        applySettings() // the Background settings may have changed
        return START_STICKY
    }

    /**
     * SandFly when that's the choice. While the phone is locked, only a SandFly that
     * can speak for other apps then gets it (the user allows FastSMRW while locked in
     * SandFly); with an older SandFly, text-to-speech reads instead.
     */
    private fun speakInBackground(text: String) {
        val useSandfly = BackgroundPrefs.speechOutput(this) == BackgroundPrefs.OUTPUT_SANDFLY &&
            (!isLocked() || SandflySpeech.speaksWhileLocked(this))
        if (useSandfly) sandflySpeech().speak(text) else ttsSpeech().speak(text)
    }

    private fun isLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    @Synchronized
    private fun sandflySpeech(): SandflySpeech =
        sandfly ?: SandflySpeech(this).also { sandfly = it }

    /** Text-to-speech on the chosen engine, started on first use. */
    @Synchronized
    private fun ttsSpeech(): BackgroundSpeech {
        val engine = BackgroundPrefs.ttsEngine(this)
        speech?.takeIf { speechEngine == engine }?.let { return it }
        speech?.shutdown()
        return BackgroundSpeech(this, engine).also { speech = it; speechEngine = engine }
    }

    /** Match speech and the wake lock to the current Background settings. */
    @Synchronized
    private fun applySettings() {
        val read = BackgroundPrefs.readInBackground(this)
        // A changed engine: drop the old voice; the next post starts the new one.
        if (!read || speechEngine != BackgroundPrefs.ttsEngine(this)) {
            speech?.shutdown()
            speech = null
            speechEngine = null
        }
        if (!read || BackgroundPrefs.speechOutput(this) != BackgroundPrefs.OUTPUT_SANDFLY) {
            sandfly?.shutdown()
            sandfly = null
        }
        // Reading posts as they arrive needs the processor awake with the screen
        // off; only hold it for that.
        if (read) {
            if (wakeLock == null) {
                wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FastSMRW:background")
                    .apply { setReferenceCounted(false); acquire() }
            }
        } else {
            wakeLock?.release()
            wakeLock = null
        }
    }

    override fun onDestroy() {
        FastSmApp.of(this).removeCoreListener(listener)
        speech?.shutdown()
        speech = null
        sandfly?.shutdown()
        sandfly = null
        wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Running in the background.")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "background"
        private const val NOTIFICATION_ID = 4100
        private const val ACTION_STOP = "me.masonasons.fastsm.background.STOP"

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Running in the background", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "The notification shown while FastSMRW runs in the background."
                        setShowBadge(false)
                        setSound(null, null)
                    }
            )
        }

        /**
         * Start or stop the service to match "Run in the background", and pass on
         * changed settings to a running one. Call while a window is on screen:
         * Android doesn't let an app start a foreground service from the background.
         */
        fun sync(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            if (BackgroundPrefs.runInBackground(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
