package com.example.checkitout.util.context

import android.content.Context
import android.location.Location
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import com.example.checkitout.data.LikeContext
import com.example.checkitout.data.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Orchestrates parallel collection of all context signals at the moment of a like.
 * Each individual collector is best-effort: failures, timeouts, and missing
 * permissions yield null fields rather than aborting the whole snapshot.
 */
object LikeContextCollector {

    private const val WEB_TIMEOUT_MS = 4_500L
    private const val SENSOR_TIMEOUT_MS = 1_500L

    suspend fun collect(context: Context, track: TrackInfo): LikeContext = coroutineScope {
        // ---- Instant / sync signals ----
        val time = collectTime(track.observedAt)
        val audio = AudioRouteCollector.collect(context)

        // ---- Async signals (sensors + network) ----
        val sensorDef = async(Dispatchers.Default) {
            withTimeoutOrNull(SENSOR_TIMEOUT_MS) { SensorContextCollector.collect(context) }
        }
        val locDef = async(Dispatchers.IO) {
            withTimeoutOrNull(WEB_TIMEOUT_MS) { LocationCollector.collect(context) }
        }
        val sensor = sensorDef.await()
        val loc = locDef.await()

        // Web calls that depend on location
        val weatherDef = async(Dispatchers.IO) {
            if (loc != null) withTimeoutOrNull(WEB_TIMEOUT_MS) {
                WeatherCollector.collect(loc.latitude, loc.longitude)
            } else null
        }
        val placeDef = async(Dispatchers.IO) {
            if (loc != null) withTimeoutOrNull(WEB_TIMEOUT_MS) {
                ReverseGeoCollector.collect(loc.latitude, loc.longitude)
            } else null
        }
        // Web calls that depend on track metadata
        val spotifyDef = async(Dispatchers.IO) {
            withTimeoutOrNull(WEB_TIMEOUT_MS) { SpotifyCollector.collect(track) }
        }
        val lyricsDef = async(Dispatchers.IO) {
            withTimeoutOrNull(WEB_TIMEOUT_MS) { LyricsCollector.collect(track) }
        }

        val weather = weatherDef.await()
        val place = placeDef.await()
        val spotify = spotifyDef.await()
        val lyrics = lyricsDef.await()

        LikeContext(
            tzId = time.tzId,
            dayOfWeek = time.dayOfWeek,
            hourOfDay = time.hourOfDay,
            timeBucket = time.bucket,
            audioOutput = audio?.output,
            btDeviceName = audio?.btName,
            lat = loc?.latitude,
            lng = loc?.longitude,
            placeLabel = place,
            activity = sensor?.activity,
            stepCount = sensor?.stepCount,
            accelMagnitude = sensor?.accelMagnitude,
            weather = weather?.code,
            tempC = weather?.tempC,
            humidityPct = weather?.humidityPct,
            spotifyId = spotify?.spotifyId,
            bpm = spotify?.bpm,
            energy = spotify?.energy,
            valence = spotify?.valence,
            danceability = spotify?.danceability,
            acousticness = spotify?.acousticness,
            instrumentalness = spotify?.instrumentalness,
            musicKey = spotify?.musicKey,
            loudness = spotify?.loudness,
            lyricsSnippet = lyrics,
        )
    }

    // ---------------- Time ----------------

    private data class TimeFields(
        val tzId: String,
        val dayOfWeek: Int,
        val hourOfDay: Int,
        val bucket: String,
    )

    private fun collectTime(epochMs: Long): TimeFields {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
        val hour = zdt.hour
        val bucket = when (hour) {
            in 5..10 -> "morning"
            in 11..16 -> "afternoon"
            in 17..21 -> "evening"
            in 22..23 -> "night"
            else -> "late_night" // 0..4
        }
        return TimeFields(
            tzId = zdt.zone.id,
            dayOfWeek = zdt.dayOfWeek.value,
            hourOfDay = hour,
            bucket = bucket,
        )
    }
}

// ============================================================================
// Audio routing
// ============================================================================

object AudioRouteCollector {
    data class AudioRoute(val output: String, val btName: String?)

    fun collect(context: Context): AudioRoute? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // Pick the most "specific" output that's currently routable
        val priority = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
        val selected = priority.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
            ?: devices.firstOrNull()
            ?: return null
        val label = when (selected.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADSET"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
            else -> "OTHER"
        }
        val btName = if (label == "BLUETOOTH") selected.productName?.toString() else null
        return AudioRoute(label, btName)
    }
}
