package com.example.checkitout.links

import android.net.Uri
import com.example.checkitout.util.Http
import org.json.JSONObject

/** Cross-service link conversion via Odesli / song.link (no key; ~10 requests/min). */
object Odesli {

    data class Links(val spotifyUrl: String?, val appleMusicUrl: String?)

    fun links(sourceUrl: String, country: String?): Links? {
        val url = "https://api.song.link/v1-alpha.1/links?songIfSingle=true&url=" + Uri.encode(sourceUrl) +
            (country?.takeIf { it.matches(Regex("[A-Z]{2}")) }?.let { "&userCountry=$it" }.orEmpty())
        val body = Http.get(url, timeoutMs = 8_000) ?: return null
        val platforms = runCatching { JSONObject(body).optJSONObject("linksByPlatform") }.getOrNull()
            ?: return null
        return Links(
            spotifyUrl = platforms.urlOf("spotify")?.takeIf { it.startsWith("https://open.spotify.com/") },
            appleMusicUrl = platforms.urlOf("appleMusic")?.takeIf { it.startsWith("https://music.apple.com/") },
        )
    }

    private fun JSONObject.urlOf(platform: String): String? =
        optJSONObject(platform)?.optString("url")?.takeIf { it.isNotBlank() }
}
