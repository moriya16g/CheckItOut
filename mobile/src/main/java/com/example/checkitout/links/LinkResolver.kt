package com.example.checkitout.links

import com.example.checkitout.data.LikedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Key-free Spotify / Apple Music URL resolution:
 * 1. IDs exposed by the player's MediaSession (e.g. `spotify:track:...`)
 * 2. Odesli conversion from whichever service URL is already known
 * 3. iTunes text search as the last resort for Apple Music
 */
object LinkResolver {
    const val SPOTIFY_PACKAGE = "com.spotify.music"
    const val APPLE_MUSIC_PACKAGE = "com.apple.android.music"

    private val SPOTIFY_URI = Regex("""spotify:track:([A-Za-z0-9]{22})""")
    private val SPOTIFY_URL = Regex("""open\.spotify\.com/(?:intl-[A-Za-z-]+/)?track/([A-Za-z0-9]{22})""")
    private val APPLE_URL = Regex("""https://music\.apple\.com/\S+""")
    private val APPLE_CATALOG_ID = Regex("""\d{6,}""")

    data class Links(val spotifyUrl: String?, val appleMusicUrl: String?) {
        val spotifyId: String? get() = spotifyUrl?.let(::spotifyTrackId)
    }

    suspend fun resolve(track: LikedTrack): Links = withContext(Dispatchers.IO) {
        val ids = listOfNotNull(track.mediaId, track.mediaUri)
        val country = Locale.getDefault().country.uppercase(Locale.ROOT).takeIf { it.isNotEmpty() }

        var spotify = track.spotifyUrl
            ?: ids.firstNotNullOfOrNull(::spotifyTrackId)?.let { "https://open.spotify.com/track/$it" }
        var apple = track.appleMusicUrl
            ?: ids.firstNotNullOfOrNull { APPLE_URL.find(it)?.value }
            ?: appleCatalogId(track.packageName, ids)
                ?.let { ITunesSearch.lookupSongUrl(it, country, track.title, track.artist) }

        if (apple == null && spotify != null) {
            apple = Odesli.links(spotify, country)?.appleMusicUrl
        }
        if (apple == null) {
            apple = ITunesSearch.findSongUrl(track.title, track.artist, country)
        }
        if (spotify == null && apple != null) {
            spotify = Odesli.links(apple, country)?.spotifyUrl
        }
        Links(spotify, apple)
    }

    fun spotifyTrackId(s: String): String? =
        (SPOTIFY_URI.find(s) ?: SPOTIFY_URL.find(s))?.groupValues?.get(1)

    private fun appleCatalogId(packageName: String, ids: List<String>): String? =
        if (packageName == APPLE_MUSIC_PACKAGE) ids.firstOrNull { APPLE_CATALOG_ID.matches(it) } else null
}
