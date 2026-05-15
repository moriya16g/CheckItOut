package com.example.checkitout.action

import android.content.Context
import android.util.Log
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.data.AppDatabase
import com.example.checkitout.data.LocalDbSink
import com.example.checkitout.data.PlaylistSink
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.util.Feedback
import com.example.checkitout.util.Speaker
import com.example.checkitout.util.context.LikeContextCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry-point shared by every trigger (volume long-press, headset triple-click,
 * QS tile, in-app button). Resolves the best candidate from the recent buffer,
 * writes it through every configured [PlaylistSink], and speaks a TTS confirmation.
 *
 * After the instant feedback fires, a background coroutine collects the rich
 * [com.example.checkitout.data.LikeContext] (time, location, weather, audio
 * routing, sensors, Spotify audio-features, lyrics) and writes those columns
 * onto the just-inserted DB row. UI shows the row immediately; analytical
 * fields appear when the collectors finish (typically <5s).
 */
object LikeAction {
    private const val TAG = "LikeAction"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            Feedback.noTrack(context)
            return
        }
        val sinks = app.container.sinks
        scope.launch {
            val results = sinks.map { sink -> sink to sink.add(track) }
            val firstOk = results.firstOrNull { it.second.isSuccess }
            if (firstOk != null) {
                Log.i(TAG, "liked: ${track.displayName()} -> ${firstOk.first.displayName}")
                Speaker.speakAdded(context, track, firstOk.first)
                Feedback.success(context)
                attachContextAsync(context, sinks, track)
            } else {
                Log.e(TAG, "all sinks failed: ${results.map { it.second.exceptionOrNull()?.message }}")
                Speaker.speakNoTrack(context)
                Feedback.failure(context)
            }
        }
    }

    private fun attachContextAsync(
        context: Context,
        sinks: List<PlaylistSink>,
        track: TrackInfo,
    ) {
        val dbSink = sinks.filterIsInstance<LocalDbSink>().firstOrNull() ?: return
        val rowId = dbSink.lastInsertedId ?: return
        scope.launch {
            try {
                val ctx = LikeContextCollector.collect(context, track)
                AppDatabase.get(context).likedTrackDao().attachContext(
                    id = rowId,
                    tzId = ctx.tzId,
                    dayOfWeek = ctx.dayOfWeek,
                    hourOfDay = ctx.hourOfDay,
                    timeBucket = ctx.timeBucket,
                    positionMs = track.positionMs,
                    durationMs = track.durationMs,
                    positionPct = if (track.positionMs != null && track.durationMs != null && track.durationMs > 0)
                        (track.positionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
                    else null,
                    audioOutput = ctx.audioOutput,
                    btDeviceName = ctx.btDeviceName,
                    lat = ctx.lat,
                    lng = ctx.lng,
                    placeLabel = ctx.placeLabel,
                    activity = ctx.activity,
                    stepCount = ctx.stepCount,
                    accelMagnitude = ctx.accelMagnitude,
                    weather = ctx.weather,
                    tempC = ctx.tempC,
                    humidityPct = ctx.humidityPct,
                    spotifyId = ctx.spotifyId,
                    bpm = ctx.bpm,
                    energy = ctx.energy,
                    valence = ctx.valence,
                    danceability = ctx.danceability,
                    acousticness = ctx.acousticness,
                    instrumentalness = ctx.instrumentalness,
                    musicKey = ctx.musicKey,
                    loudness = ctx.loudness,
                    lyricsSnippet = ctx.lyricsSnippet,
                )
                Log.i(TAG, "context attached id=$rowId place=${ctx.placeLabel} audio=${ctx.audioOutput}")
            } catch (t: Throwable) {
                Log.w(TAG, "context attach failed: ${t.message}")
            }
        }
    }
}
