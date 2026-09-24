package com.example.checkitout.util.context

import android.util.Base64
import com.example.checkitout.BuildConfig
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.util.Http
import org.json.JSONObject

/**
 * Spotify Web API audio-features lookup.
 *
 * Requires that you provision a Spotify app at https://developer.spotify.com/
 * and put credentials into your `local.properties`:
 *
 * ```
 * spotify.client.id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * spotify.client.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * ```
 *
 * If credentials are absent, this collector silently returns null.
 *
 * Uses Client Credentials flow (no user OAuth required).
 */
object SpotifyCollector {

    data class Features(
        val spotifyId: String,
        val bpm: Float?,
        val energy: Float?,
        val valence: Float?,
        val danceability: Float?,
        val acousticness: Float?,
        val instrumentalness: Float?,
        val musicKey: Int?,
        val loudness: Float?,
    )

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAtMs: Long = 0L

    suspend fun collect(track: TrackInfo): Features? {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
        if (clientId.isBlank() || clientSecret.isBlank()) return null

        val token = getToken(clientId, clientSecret) ?: return null
        val trackId = searchTrack(track, token) ?: return null
        return fetchFeatures(trackId, token)
    }

    private fun getToken(id: String, secret: String): String? {
        val cached = cachedToken
        if (cached != null && System.currentTimeMillis() < tokenExpiresAtMs - 30_000) return cached
        val basic = Base64.encodeToString("$id:$secret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = Http.postForm(
            url = "https://accounts.spotify.com/api/token",
            formBody = mapOf("grant_type" to "client_credentials"),
            headers = mapOf("Authorization" to "Basic $basic"),
        ) ?: return null
        return try {
            val obj = JSONObject(body)
            val tok = obj.optString("access_token").takeIf { it.isNotBlank() } ?: return null
            val expSec = obj.optInt("expires_in", 3_600)
            cachedToken = tok
            tokenExpiresAtMs = System.currentTimeMillis() + expSec * 1_000L
            tok
        } catch (_: Throwable) {
            null
        }
    }

    private fun searchTrack(track: TrackInfo, token: String): String? {
        val q = buildString {
            append("track:\"").append(track.title).append("\"")
            if (!track.artist.isNullOrBlank()) {
                append(" artist:\"").append(track.artist).append("\"")
            }
        }
        val url = "https://api.spotify.com/v1/search?type=track&limit=1&q=" +
                java.net.URLEncoder.encode(q, "UTF-8")
        val body = Http.get(url, headers = mapOf("Authorization" to "Bearer $token")) ?: return null
        return try {
            val items = JSONObject(body).optJSONObject("tracks")?.optJSONArray("items")
            items?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchFeatures(trackId: String, token: String): Features? {
        val body = Http.get(
            "https://api.spotify.com/v1/audio-features/$trackId",
            headers = mapOf("Authorization" to "Bearer $token"),
        ) ?: return null
        return try {
            val o = JSONObject(body)
            Features(
                spotifyId = trackId,
                bpm = (o.opt("tempo") as? Number)?.toFloat(),
                energy = (o.opt("energy") as? Number)?.toFloat(),
                valence = (o.opt("valence") as? Number)?.toFloat(),
                danceability = (o.opt("danceability") as? Number)?.toFloat(),
                acousticness = (o.opt("acousticness") as? Number)?.toFloat(),
                instrumentalness = (o.opt("instrumentalness") as? Number)?.toFloat(),
                musicKey = (o.opt("key") as? Number)?.toInt(),
                loudness = (o.opt("loudness") as? Number)?.toFloat(),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
