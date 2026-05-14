package com.example.checkitout.util

import android.content.Context
import android.net.Uri
import com.example.checkitout.data.LikedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes the liked-tracks list to a user-chosen file.
 *
 * Apple Music URLs are resolved on demand (cached after first call). Spotify
 * and Last.fm URLs are deterministic so no network is needed for them.
 *
 * The caller obtains the destination URI via the system file picker
 * (ACTION_CREATE_DOCUMENT) and passes it in.
 */
object Exporter {

    enum class Format { CSV, MARKDOWN }

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    suspend fun export(
        context: Context,
        target: Uri,
        tracks: List<LikedTrack>,
        format: Format,
        resolveAppleMusic: Boolean = true,
    ): Result<Int> = runCatching {
        val rows = enrich(tracks, resolveAppleMusic)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "wt")?.use { os ->
                os.bufferedWriter(Charsets.UTF_8).use { w ->
                    when (format) {
                        Format.CSV -> writeCsv(w, rows)
                        Format.MARKDOWN -> writeMarkdown(w, rows)
                    }
                }
            } ?: error("Cannot open $target for writing")
        }
        rows.size
    }

    private data class Row(
        val title: String,
        val artist: String,
        val album: String,
        val source: String,
        val likedAtIso: String,
        val spotify: String,
        val appleMusic: String,
        val lastFm: String,
    )

    private suspend fun enrich(tracks: List<LikedTrack>, resolveApple: Boolean): List<Row> =
        coroutineScope {
            tracks.map { t ->
                async {
                    val apple = if (resolveApple) MusicLinks.resolveAppleMusic(t).orEmpty() else ""
                    Row(
                        title = t.title,
                        artist = t.artist.orEmpty(),
                        album = t.album.orEmpty(),
                        source = t.packageName,
                        likedAtIso = isoFmt.format(Date(t.likedAt)),
                        spotify = MusicLinks.spotifySearchUrl(t),
                        appleMusic = apple,
                        lastFm = MusicLinks.lastFmUrl(t),
                    )
                }
            }.map { it.await() }
        }

    private fun writeCsv(w: Appendable, rows: List<Row>) {
        w.append("title,artist,album,source,liked_at,spotify_url,apple_music_url,lastfm_url\n")
        for (r in rows) {
            w.append(csv(r.title)).append(',')
                .append(csv(r.artist)).append(',')
                .append(csv(r.album)).append(',')
                .append(csv(r.source)).append(',')
                .append(csv(r.likedAtIso)).append(',')
                .append(csv(r.spotify)).append(',')
                .append(csv(r.appleMusic)).append(',')
                .append(csv(r.lastFm)).append('\n')
        }
    }

    private fun writeMarkdown(w: Appendable, rows: List<Row>) {
        w.append("# CheckItOut liked tracks\n\n")
        w.append("Exported: ").append(isoFmt.format(Date())).append("  \n")
        w.append("Count: ").append(rows.size.toString()).append("\n\n")
        w.append("| # | Track | Source | Liked at | Links |\n")
        w.append("|---|---|---|---|---|\n")
        rows.forEachIndexed { i, r ->
            val name = if (r.artist.isNotBlank()) "${md(r.artist)} - ${md(r.title)}" else md(r.title)
            val links = buildList {
                add("[Spotify](${r.spotify})")
                if (r.appleMusic.isNotBlank()) add("[Apple Music](${r.appleMusic})")
                add("[Last.fm](${r.lastFm})")
            }.joinToString(" / ")
            w.append("| ").append((i + 1).toString())
                .append(" | ").append(name)
                .append(" | ").append(md(r.source))
                .append(" | ").append(r.likedAtIso)
                .append(" | ").append(links)
                .append(" |\n")
        }
    }

    private fun csv(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun md(s: String): String =
        s.replace("|", "\\|").replace("\n", " ")
}
