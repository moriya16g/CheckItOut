package com.example.checkitout.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.R
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.service.LikeReceiver

/**
 * Home-screen widget with two tap targets:
 *  - "👍 Like"           → likes the current (or smart-recent) track
 *  - "Like previous"     → likes the song that was playing before
 *
 * On Android 12+ this same widget can also be placed in the lock-screen
 * widget area on devices that expose one.
 *
 * Widget content (the now-playing line) is refreshed by [CaptureService]
 * via [updateAll] every time the recent buffer changes.
 */
class LikeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? CheckItOutApp
        val track = app?.container?.recentBuffer?.current()
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, render(context, track)) }
    }

    companion object {
        fun updateAll(context: Context, track: TrackInfo?) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, LikeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = render(context, track)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun render(context: Context, track: TrackInfo?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_like)
            val nowPlaying = track?.let {
                if (!it.artist.isNullOrBlank()) "${it.artist} - ${it.title}" else it.title
            } ?: context.getString(R.string.widget_idle)
            views.setTextViewText(R.id.widget_now_playing, nowPlaying)
            views.setOnClickPendingIntent(R.id.widget_like_btn, pending(context, 0, REQ_NOW))
            views.setOnClickPendingIntent(R.id.widget_like_prev_btn, pending(context, 1, REQ_PREV))
            return views
        }

        private fun pending(context: Context, historyIndex: Int, reqCode: Int): PendingIntent {
            val intent = Intent(context, LikeReceiver::class.java).apply {
                action = LikeReceiver.ACTION_LIKE
                putExtra(LikeReceiver.EXTRA_HISTORY_INDEX, historyIndex)
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context, reqCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private const val REQ_NOW = 21
        private const val REQ_PREV = 22
    }
}
