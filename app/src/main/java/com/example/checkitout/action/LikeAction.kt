package com.example.checkitout.action

import android.content.Context
import android.util.Log
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.data.PlaylistSink
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.util.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry-point shared by every trigger (volume long-press, headset triple-click,
 * QS tile, in-app button). Resolves the best candidate from the recent buffer,
 * writes it through every configured [PlaylistSink], and speaks a TTS confirmation.
 */
object LikeAction {
    private const val TAG = "LikeAction"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @param historyIndex 0 = most recent / smartly chosen, 1 = previous, ...
     * Index 0 uses [com.example.checkitout.data.RecentBuffer.bestCandidate] which
     * already handles the "song just switched" race.
     */
    fun trigger(context: Context, historyIndex: Int = 0) {
        val app = context.applicationContext as CheckItOutApp
        val now = System.currentTimeMillis()
        val track: TrackInfo? = if (historyIndex == 0) {
            app.container.recentBuffer.bestCandidate(now)
        } else {
            app.container.recentBuffer.at(historyIndex)
        }
        if (track == null) {
            Log.w(TAG, "no track to like")
            Speaker.speakNoTrack(context)
            return
        }
        val sinks = app.container.sinks
        scope.launch {
            val results = sinks.map { sink -> sink to sink.add(track) }
            val firstOk = results.firstOrNull { it.second.isSuccess }
            if (firstOk != null) {
                Log.i(TAG, "liked: ${track.displayName()} -> ${firstOk.first.displayName}")
                Speaker.speakAdded(context, track, firstOk.first)
            } else {
                Log.e(TAG, "all sinks failed: ${results.map { it.second.exceptionOrNull()?.message }}")
                Speaker.speakNoTrack(context)
            }
        }
    }
}
