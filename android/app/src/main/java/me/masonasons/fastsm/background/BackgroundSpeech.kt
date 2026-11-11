package me.masonasons.fastsm.background

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech

/**
 * Text-to-speech for auto-read posts while FastSMRW is in the background, through
 * the engine chosen under Settings, Background (empty: the system default). Text
 * spoken before the engine is ready is queued, not dropped.
 */
class BackgroundSpeech(context: Context, engine: String) {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var ready = false
    private var serial = 0L

    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        { status ->
            synchronized(lock) {
                ready = status == TextToSpeech.SUCCESS
                if (ready) while (pending.isNotEmpty()) say(pending.removeFirst())
                else pending.clear()
            }
        },
        engine.ifEmpty { null },
    ).apply {
        // The accessibility stream, so it follows the screen reader's volume.
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(lock) {
            if (ready) say(text) else if (pending.size < 50) pending.addLast(text)
        }
    }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "fastsm-${serial++}")
    }

    /** Stop speaking (the window came back; the screen reader takes over). */
    fun stop() {
        synchronized(lock) { pending.clear() }
        tts.stop()
    }

    fun shutdown() {
        synchronized(lock) { pending.clear(); ready = false }
        tts.shutdown()
    }

    companion object {
        /** Installed engines as (package, label), for the engine setting. */
        fun engines(context: Context, onResult: (List<Pair<String, String>>) -> Unit) {
            var probe: TextToSpeech? = null
            probe = TextToSpeech(context.applicationContext) { _ ->
                val list = probe?.engines.orEmpty().map { it.name to it.label }
                probe?.shutdown()
                onResult(list)
            }
        }
    }
}
