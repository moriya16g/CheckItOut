package com.example.checkitout.data

/**
 * Represents a single observed track (a snapshot of what was playing at some moment).
 */
data class TrackInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    val packageName: String,
    /** epoch millis when this snapshot was observed */
    val observedAt: Long,
) {
    /** Key used for de-duplication across consecutive notifications. */
    val identity: String get() = "${packageName}|${title}|${artist.orEmpty()}"

    fun displayName(): String =
        if (!artist.isNullOrBlank()) "$artist - $title" else title
}
