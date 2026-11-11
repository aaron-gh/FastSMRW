package me.masonasons.fastsm

import android.app.Application
import me.masonasons.fastsm.core.FastSmCore
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the process's one core session. It lives here, not in the screen's view
 * model, so it outlives the window: with "Run in the background" on, the app can
 * be closed (or swiped away) and the core keeps streaming, playing sounds and
 * auto-reading, and a reopened window reattaches to the same session.
 *
 * Core events fan out to every attached listener (the view model while a window
 * exists, the background service while it runs). They arrive on the core's thread.
 */
class FastSmApp : Application() {

    private val listeners = CopyOnWriteArrayList<FastSmCore.EventListener>()

    val core: FastSmCore by lazy {
        FastSmCore.create(this).also { core ->
            core.setEventListener { e -> listeners.forEach { it.onEvent(e) } }
        }
    }

    /** "start" is sent once per process; a later window asks for sync_ui instead. */
    @Volatile
    var started = false

    /** The automatic update check runs once per process, not once per window. */
    @Volatile
    var startupUpdateChecked = false

    /** A pause was sent and no resume yet (the window left the screen). */
    @Volatile
    var paused = false

    /** A FastSMRW window is on screen (between onResume and onPause). */
    @Volatile
    var inForeground = false

    fun addCoreListener(l: FastSmCore.EventListener) {
        listeners += l
    }

    fun removeCoreListener(l: FastSmCore.EventListener) {
        listeners -= l
    }

    /** Attach a window's listener and bring it up to date with the session. */
    fun attachUi(l: FastSmCore.EventListener) {
        addCoreListener(l)
        if (!started) {
            started = true
            core.dispatch("start") // load persisted accounts + timelines
        } else {
            core.dispatch("sync_ui") // the session kept running: send its state
        }
    }

    companion object {
        fun of(context: android.content.Context): FastSmApp =
            context.applicationContext as FastSmApp

        /** True when [e] is an auto-read announcement (new posts to speak). */
        fun isAutoRead(e: JSONObject) =
            e.optString("event") == "announce" && e.optBoolean("auto_read")
    }
}
