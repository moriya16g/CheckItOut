package com.example.checkitout.action

import android.content.Context
import android.util.Log
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.data.AppDatabase
import com.example.checkitout.data.PlaylistSink
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.data.TriggerSource
import com.example.checkitout.links.LinkResolveWorker
import com.example.checkitout.service.MediaNotificationListener
import com.example.checkitout.util.Feedback
import com.example.checkitout.util.Speaker
import com.example.checkitout.util.context.LikeContextCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry-point shared by every trigger (Wear OS, QS tile, widget, in-app button).
 * Resolves the best candidate from the recent buffer and writes it through every
 * configured [PlaylistSink].
 *
 * After the row is saved, service URLs are resolved by [LinkResolveWorker] and the
 * rich [com.example.checkitout.data.LikeContext] is collected in the background.
 */
object LikeAction {
    private const val TAG = "LikeAction"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed interface Outcome {
        data class Saved(val track: TrackInfo, val sink: PlaylistSink) : Outcome
        data object NoTrack : Outcome
        data object Failed : Outcome
    }

    /** Fire-and-forget variant for phone-side triggers; gives TTS / haptic feedback. */
    fun trigger(context: Context, historyIndex: Int = 0, source: TriggerSource = TriggerSource.APP) {
        scope.launch {
            when (val outcome = like(context, source, historyIndex = historyIndex)) {
                is Outcome.Saved -> {
                    Speaker.speakAdded(context, outcome.track, outcome.sink)
                    Feedback.success(context)
                }
                Outcome.NoTrack -> {
                    Speaker.speakNoTrack(context)
                    Feedback.noTrack(context)
                }
                Outcome.Failed -> {
                    Speaker.speakNoTrack(context)
                    Feedback.failure(context)
                }
            }
        }
    }

    suspend fun like(
        context: Context,
        source: TriggerSource,
        pressedAt: Long = System.currentTimeMillis(),
        historyIndex: Int = 0,
    ): Outcome {
        val app = context.applicationContext as CheckItOutApp
        val track: TrackInfo? = if (historyIndex == 0) {
            app.container.recentBuffer.bestCandidate(pressedAt)
                ?: MediaNotificationListener.readNow(app)
        } else {
            app.container.recentBuffer.at(historyIndex)
        }
        if (track == null) {
            Log.w(TAG, "no track to like")
            return Outcome.NoTrack
        }
        val results = app.container.sinks.map { sink -> sink to sink.add(track, pressedAt, source) }
        val firstOk = results.firstOrNull { it.second.isSuccess }
        if (firstOk == null) {
            Log.e(TAG, "all sinks failed: ${results.map { it.second.exceptionOrNull()?.message }}")
            return Outcome.Failed
        }
        Log.i(TAG, "liked: ${track.displayName()} -> ${firstOk.first.displayName} via $source")
        results.firstNotNullOfOrNull { it.second.getOrNull() }?.let { rowId ->
            LinkResolveWorker.enqueue(app, rowId)
            attachContextAsync(app, rowId, track)
        }
        return Outcome.Saved(track, firstOk.first)
    }

    private fun attachContextAsync(
        context: Context,
        rowId: Long,
        track: TrackInfo,
    ) {
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
                    updatedAt = System.currentTimeMillis(),
                )
                Log.i(TAG, "context attached id=$rowId place=${ctx.placeLabel} audio=${ctx.audioOutput}")
            } catch (t: Throwable) {
                Log.w(TAG, "context attach failed: ${t.message}")
            }
        }
    }
}
