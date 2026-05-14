package com.example.checkitout.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import com.example.checkitout.action.LikeAction

/**
 * Wired headset / Bluetooth media-button receiver.
 *
 * We don't try to fight the music app for play/pause; instead we detect a
 * *triple-press* of the play/pause button as our trigger. This matches the
 * convention used by many third-party "next track" apps.
 *
 * NOTE: For this to win against the music app you typically need to designate
 * CheckItOut as the default media-button receiver via [android.media.AudioManager]
 * or hold a MediaSession with higher priority. In MVP we register here and
 * also offer the in-app button + QS tile + volume long-press as alternatives.
 */
class HeadsetButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) return
        if (!event.isHeadsetButton()) return

        val now = SystemClock.uptimeMillis()
        synchronized(LOCK) {
            timestamps.addLast(now)
            while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= 3) {
                timestamps.clear()
                LikeAction.trigger(context.applicationContext, historyIndex = 0)
            }
        }
    }

    private fun KeyEvent.isHeadsetButton(): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
        else -> false
    }

    companion object {
        private val LOCK = Any()
        private val timestamps = ArrayDeque<Long>()
        private const val WINDOW_MS = 900L
    }
}
