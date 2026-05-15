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
    suspend fun add(track: TrackInfo): Result<Unit>
}

class LocalDbSink(private val context: Context) : PlaylistSink {
    override val id: String = "default"
    override val displayName: String = "ローカル保存"

    /**
     * Row id of the most recent successful insert. Used by the like flow to
     * attach asynchronously-collected context (location, weather, audio
     * features, etc.) after instant UI feedback has fired.
     */
    @Volatile var lastInsertedId: Long? = null
        private set

    override suspend fun add(track: TrackInfo): Result<Unit> = runCatching {
        val id = AppDatabase.get(context).likedTrackDao().insert(
            LikedTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                packageName = track.packageName,
                likedAt = System.currentTimeMillis(),
                playlist = this.id,
                positionMs = track.positionMs,
                durationMs = track.durationMs,
                positionPct = if (track.positionMs != null && track.durationMs != null && track.durationMs > 0)
                    (track.positionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
                else null,
            )
        )
        lastInsertedId = id
    }.map { }
}
