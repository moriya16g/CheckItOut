package com.example.checkitout.sync

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.checkitout.data.AppDatabase
import com.example.checkitout.data.LikedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages bi-directional sync of liked tracks through a single JSON file
 * stored on a user-chosen location (which may be on Google Drive, Dropbox,
 * OneDrive, local storage, etc.).
 *
 * The user picks the file directly via the Storage Access Framework's
 * single-document picker (ACTION_CREATE_DOCUMENT for new, ACTION_OPEN_DOCUMENT
 * for existing). This works with cloud providers that do not implement the
 * tree (folder) document API.
 *
 * ## Sync algorithm
 * 1. Read the remote JSON file (array of track objects keyed by `syncId`).
 * 2. Merge: any `syncId` that exists remotely but not locally -> insert into DB.
 * 3. Any `syncId` that exists locally but not remotely -> add to the JSON set.
 * 4. Write the merged JSON back to the file.
 */
object SyncManager {
    private const val TAG = "SyncManager"
    const val SYNC_FILE_NAME = "checkitout_sync.json"
    private const val PREFS_NAME = "sync_prefs"
    private const val KEY_FILE_URI = "sync_file_uri"
    private const val KEY_LAST_SYNC = "last_sync_millis"

    // ──────────────────────── SAF file persistence ────────────────────────

    fun getSavedFileUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_FILE_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun saveFileUri(context: Context, uri: Uri) {
        // Take persistable permission so the URI survives reboots.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs(context).edit().putString(KEY_FILE_URI, uri.toString()).apply()
    }

    fun clearFileUri(context: Context) {
        prefs(context).edit().remove(KEY_FILE_URI).remove(KEY_LAST_SYNC).apply()
    }

    fun getLastSyncTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    // ──────────────────────── Core sync logic ────────────────────────

    /**
     * Runs a full bidirectional merge. Returns the number of new tracks imported.
     * Throws on I/O errors so the caller (WorkManager) can retry.
     */
    suspend fun sync(context: Context): Int = withContext(Dispatchers.IO) {
        val fileUri = getSavedFileUri(context)
            ?: throw IllegalStateException("No sync file configured")
        val dao = AppDatabase.get(context).likedTrackDao()

        // 1. Read existing remote data (treat empty/missing as empty array)
        val remoteJson = readJsonArray(context, fileUri)
        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteJson.length()) {
            val obj = remoteJson.getJSONObject(i)
            remoteMap[obj.getString("syncId")] = obj
        }

        // 3. Read local data
        val localTracks = dao.getAll()
        val localSyncIds = localTracks.map { it.syncId }.toSet()

        // 4. Import remote-only tracks into local DB
        var imported = 0
        for ((syncId, obj) in remoteMap) {
            if (syncId !in localSyncIds) {
                dao.insert(jsonToTrack(obj))
                imported++
            }
        }

        // 5. Add local-only tracks to remote set
        for (track in localTracks) {
            if (track.syncId !in remoteMap) {
                remoteMap[track.syncId] = trackToJson(track)
            }
        }

        // 6. Write merged JSON back
        val merged = JSONArray()
        for (obj in remoteMap.values) merged.put(obj)
        writeJsonArray(context, fileUri, merged)

        // 7. Record sync time
        prefs(context).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        Log.i(TAG, "sync complete: imported=$imported, total=${remoteMap.size}")
        imported
    }

    // ──────────────────────── SAF helpers ────────────────────────

    private fun readJsonArray(context: Context, fileUri: Uri): JSONArray {
        val text = try {
            context.contentResolver.openInputStream(fileUri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: "[]"
        } catch (e: Exception) {
            // File may have been newly created and is still empty/invalid.
            Log.w(TAG, "read failed, treating as empty: ${e.message}")
            "[]"
        }
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    private fun writeJsonArray(context: Context, fileUri: Uri, array: JSONArray) {
        context.contentResolver.openOutputStream(fileUri, "wt")?.use {
            it.bufferedWriter(Charsets.UTF_8).use { w -> w.write(array.toString(2)) }
        }
    }

    // ──────────────────────── JSON ↔ LikedTrack ────────────────────────

    private fun JSONObject.putOpt(name: String, value: Any?) {
        if (value != null) put(name, value)
    }

    private fun trackToJson(t: LikedTrack): JSONObject = JSONObject().apply {
        put("syncId", t.syncId)
        put("title", t.title)
        put("artist", t.artist ?: "")
        put("album", t.album ?: "")
        put("packageName", t.packageName)
        put("likedAt", t.likedAt)
        put("playlist", t.playlist)
        // LikeContext fields (omit when null to keep JSON compact)
        putOpt("tzId", t.tzId)
        putOpt("dayOfWeek", t.dayOfWeek)
        putOpt("hourOfDay", t.hourOfDay)
        putOpt("timeBucket", t.timeBucket)
        putOpt("positionMs", t.positionMs)
        putOpt("durationMs", t.durationMs)
        putOpt("positionPct", t.positionPct?.toDouble())
        putOpt("audioOutput", t.audioOutput)
        putOpt("btDeviceName", t.btDeviceName)
        putOpt("lat", t.lat)
        putOpt("lng", t.lng)
        putOpt("placeLabel", t.placeLabel)
        putOpt("activity", t.activity)
        putOpt("stepCount", t.stepCount)
        putOpt("accelMagnitude", t.accelMagnitude?.toDouble())
        putOpt("weather", t.weather)
        putOpt("tempC", t.tempC?.toDouble())
        putOpt("humidityPct", t.humidityPct?.toDouble())
        putOpt("spotifyId", t.spotifyId)
        putOpt("bpm", t.bpm?.toDouble())
        putOpt("energy", t.energy?.toDouble())
        putOpt("valence", t.valence?.toDouble())
        putOpt("danceability", t.danceability?.toDouble())
        putOpt("acousticness", t.acousticness?.toDouble())
        putOpt("instrumentalness", t.instrumentalness?.toDouble())
        putOpt("musicKey", t.musicKey)
        putOpt("loudness", t.loudness?.toDouble())
        putOpt("lyricsSnippet", t.lyricsSnippet)
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null
    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null
    private fun JSONObject.optFloatOrNull(name: String): Float? =
        optDoubleOrNull(name)?.toFloat()
    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun jsonToTrack(o: JSONObject): LikedTrack {
        val title = o.getString("title")
        val artist = o.optString("artist").takeIf { it.isNotBlank() }
        val likedAt = o.getLong("likedAt")
        return LikedTrack(
            title = title,
            artist = artist,
            album = o.optString("album").takeIf { it.isNotBlank() },
            packageName = o.optString("packageName", "synced"),
            likedAt = likedAt,
            playlist = o.optString("playlist", "default"),
            syncId = o.optString("syncId", LikedTrack.buildSyncId(title, artist, likedAt)),
            tzId = o.optStringOrNull("tzId"),
            dayOfWeek = o.optIntOrNull("dayOfWeek"),
            hourOfDay = o.optIntOrNull("hourOfDay"),
            timeBucket = o.optStringOrNull("timeBucket"),
            positionMs = o.optLongOrNull("positionMs"),
            durationMs = o.optLongOrNull("durationMs"),
            positionPct = o.optFloatOrNull("positionPct"),
            audioOutput = o.optStringOrNull("audioOutput"),
            btDeviceName = o.optStringOrNull("btDeviceName"),
            lat = o.optDoubleOrNull("lat"),
            lng = o.optDoubleOrNull("lng"),
            placeLabel = o.optStringOrNull("placeLabel"),
            activity = o.optStringOrNull("activity"),
            stepCount = o.optIntOrNull("stepCount"),
            accelMagnitude = o.optFloatOrNull("accelMagnitude"),
            weather = o.optStringOrNull("weather"),
            tempC = o.optFloatOrNull("tempC"),
            humidityPct = o.optFloatOrNull("humidityPct"),
            spotifyId = o.optStringOrNull("spotifyId"),
            bpm = o.optFloatOrNull("bpm"),
            energy = o.optFloatOrNull("energy"),
            valence = o.optFloatOrNull("valence"),
            danceability = o.optFloatOrNull("danceability"),
            acousticness = o.optFloatOrNull("acousticness"),
            instrumentalness = o.optFloatOrNull("instrumentalness"),
            musicKey = o.optIntOrNull("musicKey"),
            loudness = o.optFloatOrNull("loudness"),
            lyricsSnippet = o.optStringOrNull("lyricsSnippet"),
        )
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
