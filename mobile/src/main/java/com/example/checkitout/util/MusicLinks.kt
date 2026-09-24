package com.example.checkitout.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.checkitout.data.LikedTrack
import com.example.checkitout.links.ITunesSearch
import com.example.checkitout.links.LinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds and opens links to external music services for a given track.
 *
 * Exact Spotify / Apple Music URLs are resolved key-free by
 * [com.example.checkitout.links.LinkResolver] and stored on the row; the
 * helpers here are fallbacks for rows that have no stored link yet.
 */
object MusicLinks {
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
        track.appleMusicUrl?.let { return it }
        val key = identityKey(track)
        appleCache[key]?.let { return if (it == NO_MATCH) null else it }
        val url = withContext(Dispatchers.IO) {
            ITunesSearch.findSongUrl(track.title, track.artist, Locale.getDefault().country.ifEmpty { null })
        }
        appleCache[key] = url ?: NO_MATCH
        return url
    }

    /** Opens in the Spotify / Apple Music app when installed, otherwise via App Links or browser. */
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val preferredPackage = when (uri.host) {
            "open.spotify.com" -> LinkResolver.SPOTIFY_PACKAGE
            "music.apple.com" -> LinkResolver.APPLE_MUSIC_PACKAGE
            else -> null
        }
        if (preferredPackage != null) {
            try {
                context.startActivity(Intent(intent).setPackage(preferredPackage))
                return
            } catch (_: ActivityNotFoundException) {
                // Not installed; fall through to the generic handler.
            }
        }
        runCatching { context.startActivity(intent) }
    }

    private fun identityKey(t: LikedTrack): String = "${t.title}|${t.artist.orEmpty()}"

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun lastFmSegment(s: String): String =
        encode(s.trim()).replace("+", "%20")
}
