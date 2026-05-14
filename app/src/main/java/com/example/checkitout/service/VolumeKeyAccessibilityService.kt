package com.example.checkitout.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.checkitout.action.LikeAction

/**
 * Captures volume-key events even when the screen is off. This is the only
 * official API on stock Android that allows a non-system app to do that.
 *
 * Gesture: hold Volume-Down for [LONG_PRESS_MS] ms while music is playing.
 * The original volume-down event is still delivered to the system, so users
 * can keep using the button normally for short presses.
 *
 * Triple-press of either volume key triggers "like the *previous* song"
 * (useful when the song just switched and you missed it).
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private var downStart: Long = 0
    private var triggered = false

    // Triple-press detection (either key)
    private val tripleWindowMs = 800L
    private val pressTimestamps = ArrayDeque<Long>()

    override fun onServiceConnected() {
        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        serviceInfo = info
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) {
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    downStart = SystemClock.uptimeMillis()
                    triggered = false
                    recordPress(downStart)
                } else if (!triggered && code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    val held = SystemClock.uptimeMillis() - downStart
                    if (held >= LONG_PRESS_MS) {
                        triggered = true
                        Log.i(TAG, "volume long-press -> like")
                        LikeAction.trigger(applicationContext, historyIndex = 0)
                    }
                }
            }
            KeyEvent.ACTION_UP -> {
                if (triggered) {
                    // we consumed the long-press, swallow this key entirely
                    return true
                }
            }
        }
        // We never want to fully consume short presses; let the system handle volume.
        return false
    }

    private fun recordPress(now: Long) {
        pressTimestamps.addLast(now)
        while (pressTimestamps.isNotEmpty() && now - pressTimestamps.first() > tripleWindowMs) {
            pressTimestamps.removeFirst()
        }
        if (pressTimestamps.size >= 3) {
            pressTimestamps.clear()
            Log.i(TAG, "volume triple-press -> like previous")
            LikeAction.trigger(applicationContext, historyIndex = 1)
        }
    }

    companion object {
        private const val TAG = "VolA11y"
        private const val LONG_PRESS_MS = 700L
    }
}
