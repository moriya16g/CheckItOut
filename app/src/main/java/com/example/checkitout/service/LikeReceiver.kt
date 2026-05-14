package com.example.checkitout.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.checkitout.action.LikeAction

/**
 * Single broadcast entry point used by:
 *  - the foreground notification action buttons (visible on lock screen)
 *  - the home-screen widget buttons
 *
 * Carries an [EXTRA_HISTORY_INDEX] (0 = current, 1 = previous, ...).
 */
class LikeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_LIKE) return
        val idx = intent.getIntExtra(EXTRA_HISTORY_INDEX, 0)
        LikeAction.trigger(context.applicationContext, historyIndex = idx)
    }

    companion object {
        const val ACTION_LIKE = "com.example.checkitout.action.LIKE"
        const val EXTRA_HISTORY_INDEX = "history_index"
    }
}
