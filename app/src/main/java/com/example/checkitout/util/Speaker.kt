package com.example.checkitout.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.checkitout.R
import com.example.checkitout.data.PlaylistSink
import com.example.checkitout.data.TrackInfo
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny TTS wrapper. We initialise lazily on demand and shut down when the
 * utterance completes so we do not hold audio focus longer than needed.
 *
 * The screen never has to wake up for the user to hear this.
 */
object Speaker {
    @Volatile private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val pending = ArrayDeque<String>()

    private fun ensure(context: Context, then: () -> Unit) {
        val existing = tts
        if (existing != null && ready.get()) {
            then()
            return
        }
        if (existing == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.JAPANESE
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {}
                    })
                    ready.set(true)
                    val drained = pending.toList()
                    pending.clear()
                    drained.forEach { speakNow(it) }
                    then()
                }
            }
        }
    }

    private fun speakNow(text: String) {
        val t = tts ?: return
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
        }
        t.speak(text, TextToSpeech.QUEUE_ADD, params, "ck_${System.nanoTime()}")
    }

    fun speakAdded(context: Context, track: TrackInfo, sink: PlaylistSink) {
        val msg = context.getString(R.string.tts_added, track.displayName(), sink.displayName)
        speak(context, msg)
    }

    fun speakNoTrack(context: Context) {
        speak(context, context.getString(R.string.tts_no_track))
    }

    fun speak(context: Context, text: String) {
        if (ready.get()) {
            speakNow(text)
        } else {
            pending.addLast(text)
            ensure(context) {}
        }
    }
}
