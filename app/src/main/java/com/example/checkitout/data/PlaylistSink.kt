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

    override suspend fun add(track: TrackInfo): Result<Unit> = runCatching {
        AppDatabase.get(context).likedTrackDao().insert(
            LikedTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                packageName = track.packageName,
                likedAt = System.currentTimeMillis(),
                playlist = id,
            )
        )
    }.map { }
}
