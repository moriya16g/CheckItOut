package com.example.checkitout.util.context

import com.example.checkitout.data.TrackInfo
import com.example.checkitout.util.Http
import org.json.JSONObject

/**
 * Lyrics snapshot via lyrics.ovh public API. No key required, no SLA.
 *
 * We store only a short snippet (first ~600 chars after collapsing whitespace)
 * to keep DB rows compact and to stay within sensible "fair use" of the API.
 */
object LyricsCollector {

    private const val MAX_LEN = 600

    suspend fun collect(track: TrackInfo): String? {
        if (track.title.isBlank() || track.artist.isNullOrBlank()) return null
        val artistEnc = java.net.URLEncoder.encode(track.artist, "UTF-8")
        val titleEnc = java.net.URLEncoder.encode(track.title, "UTF-8")
        val url = "https://api.lyrics.ovh/v1/$artistEnc/$titleEnc"
        val body = Http.get(url) ?: return null
        return try {
            val raw = JSONObject(body).optString("lyrics").takeIf { it.isNotBlank() } ?: return null
            val collapsed = raw.replace(Regex("[\\r\\n]+"), " / ").trim()
            if (collapsed.length <= MAX_LEN) collapsed else collapsed.substring(0, MAX_LEN) + "…"
        } catch (_: Throwable) {
            null
        }
    }
}
