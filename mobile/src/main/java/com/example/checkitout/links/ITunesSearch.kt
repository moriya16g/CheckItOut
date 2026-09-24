package com.example.checkitout.links

import android.net.Uri
import com.example.checkitout.util.Http
import org.json.JSONObject
import java.text.Normalizer

/** Apple Music URLs via the public iTunes Search / Lookup API (no key required). */
object ITunesSearch {

    fun findSongUrl(title: String, artist: String?, country: String?): String? {
        val term = listOfNotNull(artist, title).joinToString(" ").trim()
        if (term.isEmpty()) return null
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=10" +
            countryParam(country) + "&term=" + Uri.encode(term)
        return results(url)
            .firstOrNull { TrackMatch.matches(title, artist, it.optString("trackName"), it.optString("artistName")) }
            ?.trackViewUrl()
    }

    /** Resolves an Apple Music catalog song id, verifying it is the expected track. */
    fun lookupSongUrl(catalogId: String, country: String?, title: String, artist: String?): String? {
        val url = "https://itunes.apple.com/lookup?id=" + Uri.encode(catalogId) + countryParam(country)
        return results(url)
            .firstOrNull { TrackMatch.matches(title, artist, it.optString("trackName"), it.optString("artistName")) }
            ?.trackViewUrl()
    }

    private fun results(url: String): List<JSONObject> {
        val body = Http.get(url, timeoutMs = 6_000) ?: return emptyList()
        val array = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.trackViewUrl(): String? =
        optString("trackViewUrl")
            .takeIf { it.startsWith("https://music.apple.com/") }
            ?.replace(Regex("[?&]uo=\\d+$"), "")

    private fun countryParam(country: String?): String =
        country?.takeIf { it.matches(Regex("[A-Z]{2}")) }?.let { "&country=$it" }.orEmpty()
}

/** Loose title/artist comparison tolerant of case, width, punctuation and "(Remastered)" suffixes. */
internal object TrackMatch {
    private val BRACKETED = Regex("""[(\[（【].*?[)\]）】]""")
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]""")

    fun matches(title: String, artist: String?, candTitle: String, candArtist: String): Boolean {
        val t = normalize(title)
        val ct = normalize(candTitle)
        if (t.isEmpty() || ct.isEmpty()) return false
        val titleOk = t == ct || t.contains(ct) || ct.contains(t)
        if (!titleOk) return false
        if (artist.isNullOrBlank()) return true
        val a = normalize(artist)
        val ca = normalize(candArtist)
        return a.isEmpty() || a.contains(ca) || ca.contains(a)
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase()
            .replace(BRACKETED, "")
            .replace(NON_ALNUM, "")
}
