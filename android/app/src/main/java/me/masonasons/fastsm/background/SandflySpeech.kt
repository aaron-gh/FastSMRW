package me.masonasons.fastsm.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import fyi.quin.backtalk.speech.ISpeechControl

/**
 * Speech through SandFly, with the user's own SandFly voice and settings, over
 * SandFly's speech API (its ISpeechControl service). SandFly says nothing until
 * the user turns on Apps, Let other apps speak, and allows FastSMRW from the
 * notification SandFly posts on the first request; it also says nothing while
 * the device is locked. Text sent before the service connects is queued.
 */
class SandflySpeech(context: Context) {
    private val app = context.applicationContext
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var speech: ISpeechControl? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(lock) {
                speech = ISpeechControl.Stub.asInterface(binder)
                while (pending.isNotEmpty()) say(pending.removeFirst())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) { speech = null }
        }
    }

    private fun bind() {
        if (bound) return
        val intent = Intent(ACTION_SPEECH_CONTROL).setPackage(PACKAGE)
        bound = runCatching { app.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(lock) {
            bind()
            if (speech != null) say(text) else if (pending.size < 20) pending.addLast(text)
        }
    }

    private fun say(text: String) {
        // Queue behind what is being said; the user's own rate, pitch and volume.
        runCatching { speech?.speak(text, false, 1f, 1f, 1f) }
    }

    fun stop() {
        synchronized(lock) {
            pending.clear()
            runCatching { speech?.stop() }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            pending.clear()
            if (bound) runCatching { app.unbindService(connection) }
            bound = false
            speech = null
        }
    }

    companion object {
        const val PACKAGE = "io.github.aaron_gh.sandfly"
        private const val ACTION_SPEECH_CONTROL = "fyi.quin.backtalk.action.SPEECH_CONTROL"

        private const val PERMISSION_WHEN_LOCKED = "$PACKAGE.permission.SPEAK_WHEN_LOCKED"

        fun isInstalled(context: Context): Boolean = runCatching {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        }.getOrDefault(false)

        /**
         * Whether the installed SandFly can speak for other apps while the phone is locked
         * (it defines SPEAK_WHEN_LOCKED). Older versions say nothing from other apps then.
         */
        fun speaksWhileLocked(context: Context): Boolean = runCatching {
            context.packageManager.getPermissionInfo(PERMISSION_WHEN_LOCKED, 0)
            true
        }.getOrDefault(false)
    }
}
