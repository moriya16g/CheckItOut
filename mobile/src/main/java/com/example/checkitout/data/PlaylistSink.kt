package com.example.checkitout.data

import android.content.Context

/**
 * Sink that receives a "liked" track. The MVP only writes to the local Room DB,
 * but this abstraction is designed so future sinks (Spotify, YouTube Music, CSV
 * export, webhooks ...) can plug in without touching the trigger code.
 */
interface PlaylistSink {
    val id: String
    val displayName: String

    /** Returns the local row id when the sink stores into Room, otherwise null. */
    suspend fun add(track: TrackInfo, likedAt: Long, source: TriggerSource): Result<Long?>
}

class LocalDbSink(private val context: Context) : PlaylistSink {
    override val id: String = "default"
    override val displayName: String = "ローカル保存"

    override suspend fun add(track: TrackInfo, likedAt: Long, source: TriggerSource): Result<Long?> = runCatching {
        AppDatabase.get(context).likedTrackDao().insert(
            LikedTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                packageName = track.packageName,
                likedAt = likedAt,
                playlist = this.id,
                positionMs = track.positionMs,
                durationMs = track.durationMs,
                positionPct = if (track.positionMs != null && track.durationMs != null && track.durationMs > 0)
                    (track.positionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
                else null,
                triggerSource = source.name,
                mediaId = track.mediaId,
                mediaUri = track.mediaUri,
            )
        )
    }
}
