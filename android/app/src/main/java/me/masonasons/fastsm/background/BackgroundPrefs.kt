package me.masonasons.fastsm.background

import android.content.Context

/**
 * The Background settings. Device-side preferences, not core settings: running
 * as a foreground service and the speech engine are Android-only (the desktop
 * apps always run in the background and speak through the screen reader; iOS
 * can't stay running).
 */
object BackgroundPrefs {
    private const val FILE = "background"
    private const val RUN = "run_in_background"
    private const val READ = "read_in_background"
    private const val ENGINE = "tts_engine"
    private const val OUTPUT = "speech_output"

    /** Through SandFly's speech API (when installed); text-to-speech while locked. */
    const val OUTPUT_SANDFLY = "sandfly"
    const val OUTPUT_TTS = "tts"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Keep running with a notification after the window closes. Off by default. */
    fun runInBackground(context: Context): Boolean = prefs(context).getBoolean(RUN, false)

    fun setRunInBackground(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(RUN, on).apply()

    /** Speak auto-read posts with text-to-speech while no window is on screen. On by default. */
    fun readInBackground(context: Context): Boolean = prefs(context).getBoolean(READ, true)

    fun setReadInBackground(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(READ, on).apply()

    /**
     * How background posts are spoken: [OUTPUT_SANDFLY] or [OUTPUT_TTS]. Defaults to
     * SandFly when it's installed. SandFly's choice reads as text-to-speech once
     * SandFly is uninstalled.
     */
    fun speechOutput(context: Context): String {
        val installed = SandflySpeech.isInstalled(context)
        val saved = prefs(context).getString(OUTPUT, null)
        return when {
            saved == OUTPUT_TTS -> OUTPUT_TTS
            installed -> OUTPUT_SANDFLY
            else -> OUTPUT_TTS
        }
    }

    fun setSpeechOutput(context: Context, output: String) =
        prefs(context).edit().putString(OUTPUT, output).apply()

    /** The text-to-speech engine's package name; empty for the system default. */
    fun ttsEngine(context: Context): String = prefs(context).getString(ENGINE, "") ?: ""

    fun setTtsEngine(context: Context, engine: String) =
        prefs(context).edit().putString(ENGINE, engine).apply()
}
