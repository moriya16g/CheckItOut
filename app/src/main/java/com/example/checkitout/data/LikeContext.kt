package com.example.checkitout.data

/**
 * Snapshot of every contextual signal collected at the moment a "like"
 * was triggered. All fields are optional and best-effort: a missing field
 * just means the signal could not be acquired (no permission, no network,
 * timeout, or the device lacks the sensor).
 *
 * The shape mirrors the columns added to [LikedTrack] (Room migration v3 -> v4).
 * Keeping the class flat with primitive fields makes it trivial to:
 *  - persist via Room without TypeConverters
 *  - serialize to the sync JSON
 *  - aggregate / GROUP BY in analysis queries
 *
 * If you add a new field, also add:
 *  1. matching column in [LikedTrack]
 *  2. ALTER TABLE in `MIGRATION_3_4`
 *  3. JSON pair in [com.example.checkitout.sync.SyncManager.trackToJson] / `jsonToTrack`
 *  4. UPDATE column in `LikedTrackDao.attachContext`
 */
data class LikeContext(
    // ---- Time ----
    val tzId: String? = null,
    val dayOfWeek: Int? = null,        // 1=Mon .. 7=Sun (ISO)
    val hourOfDay: Int? = null,        // 0..23
    val timeBucket: String? = null,    // "morning"|"afternoon"|"evening"|"night"|"late_night"

    // ---- Playback ----
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val positionPct: Float? = null,    // 0.0 .. 1.0

    // ---- Audio routing ----
    val audioOutput: String? = null,   // "SPEAKER" | "WIRED_HEADSET" | "BLUETOOTH" | "USB" | "OTHER"
    val btDeviceName: String? = null,

    // ---- Location ----
    val lat: Double? = null,
    val lng: Double? = null,
    val placeLabel: String? = null,    // reverse-geocoded (Nominatim)

    // ---- Activity (sensor-based) ----
    val activity: String? = null,      // "STILL" | "WALKING" | "RUNNING" | "VEHICLE" | "UNKNOWN"
    val stepCount: Int? = null,        // cumulative since boot (raw)
    val accelMagnitude: Float? = null, // m/s^2, mean over short sample window

    // ---- Environment (Open-Meteo) ----
    val weather: String? = null,       // "clear" | "cloudy" | "rain" | "snow" | "thunder" | "fog" | ...
    val tempC: Float? = null,
    val humidityPct: Float? = null,

    // ---- Spotify audio features ----
    val spotifyId: String? = null,
    val bpm: Float? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    val acousticness: Float? = null,
    val instrumentalness: Float? = null,
    val musicKey: Int? = null,
    val loudness: Float? = null,

    // ---- Lyrics snapshot ----
    val lyricsSnippet: String? = null,
)
