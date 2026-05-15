package com.example.checkitout.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Non-voice feedback channel so the user gets a reliable response even if TTS
 * is unavailable, muted, or delayed.
 */
object Feedback {

    fun success(context: Context) {
        vibrate(context, 28L)
        tone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    fun noTrack(context: Context) {
        vibrate(context, 40L)
        tone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    fun failure(context: Context) {
        vibrate(context, 60L)
        tone(ToneGenerator.TONE_PROP_NACK, 120)
    }

    private fun vibrate(context: Context, millis: Long) {
        runCatching {
            val vib = context.getSystemService(Vibrator::class.java) ?: return
            if (!vib.hasVibrator()) return
            vib.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun tone(toneType: Int, durationMs: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            tg.startTone(toneType, durationMs)
            tg.release()
        }
    }
}