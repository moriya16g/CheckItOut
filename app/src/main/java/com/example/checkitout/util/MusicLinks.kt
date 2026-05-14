package com.example.checkitout.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.checkitout.data.LikedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds links to external music services for a given track.
 *
 * - Spotify : search-page link (no API key needed). The Spotify app handles
 *             https://open.spotify.com/... via App Links and opens in-app.
 * - Apple Music : exact track URL resolved via the public iTunes Search API
 *                 (no auth required). The Apple Music app handles
 *                 https://music.apple.com/... via App Links.
 * - Last.fm : deterministic URL pattern using artist/track names.
 *
 * Apple Music lookups are network-bound, so they are cached in memory by
 * track identity to avoid repeated requests.
 */
object MusicLinks {
    private const val TAG = "MusicLinks"
    private val appleCache = ConcurrentHashMap<String, String>()
    /** Sentinel value stored in cache when iTunes returned no match. */
    private const val NO_MATCH = "__none__"

    fun spotifySearchUrl(track: LikedTrack): String {
        val q = listOfNotNull(track.title, track.artist).joinToString(" ")
        return "https://open.spotify.com/search/" + encode(q)
    }

    fun lastFmUrl(track: LikedTrack): String {
        // Last.fm URL convention: spaces -> "+", and a "_" placeholder if data is missing.
        val artist = track.artist?.takeIf { it.isNotBlank() }?.let { lastFmSegment(it) } ?: "_"
        val title = lastFmSegment(track.title)
        return "https://www.last.fm/music/$artist/_/$title"
    }

    /** May return null if iTunes has no match. Network call. */
    suspend fun resolveAppleMusic(track: LikedTrack): String? {
        val key = identityKey(track)
        appleCache[key]?.let { return if (it == NO_MATCH) null else it }
        val url = withContext(Dispatchers.IO) { fetchAppleMusicUrl(track) }
        appleCache[key] = url ?: NO_MATCH
        return url
    }

    private fun fetchAppleMusicUrl(track: LikedTrack): String? {
        val term = listOfNotNull(track.artist, track.title).joinToString(" ")
        if (term.isBlank()) return null
        val endpoint = "https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" +
                encode(term)
        return try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null
                results.getJSONObject(0).optString("trackViewUrl").takeIf { it.isNotBlank() }
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "iTunes lookup failed", t)
            null
        }
    }

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun identityKey(t: LikedTrack): String = "${t.title}|${t.artist.orEmpty()}"

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun lastFmSegment(s: String): String =
        encode(s.trim()).replace("+", "%20")
}
