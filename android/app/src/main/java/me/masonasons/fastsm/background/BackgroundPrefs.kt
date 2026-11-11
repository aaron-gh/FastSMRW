package me.masonasons.fastsm.background

import android.content.Context

/**
 * The Background settings. Device-side preferences, not core settings: running
 * as a foreground service is Android-only (the desktop apps always run in the
 * background; iOS can't stay running).
 */
object BackgroundPrefs {
    private const val FILE = "background"
    private const val RUN = "run_in_background"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Keep running with a notification after the window closes. Off by default. */
    fun runInBackground(context: Context): Boolean = prefs(context).getBoolean(RUN, false)

    fun setRunInBackground(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(RUN, on).apply()
}
