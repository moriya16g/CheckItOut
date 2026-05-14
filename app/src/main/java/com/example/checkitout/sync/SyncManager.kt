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
 * Manages bi-directional sync of liked tracks through a JSON file stored in a
 * user-chosen folder (which may be on Google Drive, Dropbox, OneDrive, etc.).
 *
 * ## Sync algorithm
 * 1. Read the remote JSON file (array of track objects keyed by `syncId`).
 * 2. Merge: any `syncId` that exists remotely but not locally -> insert into DB.
 * 3. Any `syncId` that exists locally but not remotely -> add to the JSON set.
 * 4. Write the merged JSON back to the file.
 *
 * The cloud provider's own sync client then pushes the file to the cloud.
 */
object SyncManager {
    private const val TAG = "SyncManager"
    private const val SYNC_FILE_NAME = "checkitout_sync.json"
    private const val PREFS_NAME = "sync_prefs"
    private const val KEY_FOLDER_URI = "sync_folder_uri"
    private const val KEY_LAST_SYNC = "last_sync_millis"

    // ──────────────────────── SAF folder persistence ────────────────────────

    fun getSavedFolderUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_FOLDER_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun saveFolderUri(context: Context, uri: Uri) {
        // Take persistable permission so the URI survives reboots.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        prefs(context).edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun clearFolderUri(context: Context) {
        prefs(context).edit().remove(KEY_FOLDER_URI).remove(KEY_LAST_SYNC).apply()
    }

    fun getLastSyncTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    // ──────────────────────── Core sync logic ────────────────────────

    /**
     * Runs a full bidirectional merge. Returns the number of new tracks imported.
     * Throws on I/O errors so the caller (WorkManager) can retry.
     */
    suspend fun sync(context: Context): Int = withContext(Dispatchers.IO) {
        val folderUri = getSavedFolderUri(context)
            ?: throw IllegalStateException("No sync folder configured")
        val dao = AppDatabase.get(context).likedTrackDao()

        // 1. Resolve or create the sync file inside the chosen folder
        val fileUri = resolveOrCreateFile(context, folderUri)

        // 2. Read existing remote data
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

    private fun resolveOrCreateFile(context: Context, folderUri: Uri): Uri {
        val resolver = context.contentResolver
        val treeUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalStateException("Cannot open folder: $folderUri")
        val existing = treeUri.findFile(SYNC_FILE_NAME)
        if (existing != null && existing.isFile) return existing.uri
        val created = treeUri.createFile("application/json", SYNC_FILE_NAME)
            ?: throw IllegalStateException("Cannot create $SYNC_FILE_NAME in $folderUri")
        // Initialize with empty array
        resolver.openOutputStream(created.uri, "wt")?.use { it.write("[]".toByteArray()) }
        return created.uri
    }

    private fun readJsonArray(context: Context, fileUri: Uri): JSONArray {
        val text = context.contentResolver.openInputStream(fileUri)?.use {
            it.bufferedReader(Charsets.UTF_8).readText()
        } ?: "[]"
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    private fun writeJsonArray(context: Context, fileUri: Uri, array: JSONArray) {
        context.contentResolver.openOutputStream(fileUri, "wt")?.use {
            it.bufferedWriter(Charsets.UTF_8).use { w -> w.write(array.toString(2)) }
        }
    }

    // ──────────────────────── JSON ↔ LikedTrack ────────────────────────

    private fun trackToJson(t: LikedTrack): JSONObject = JSONObject().apply {
        put("syncId", t.syncId)
        put("title", t.title)
        put("artist", t.artist ?: "")
        put("album", t.album ?: "")
        put("packageName", t.packageName)
        put("likedAt", t.likedAt)
        put("playlist", t.playlist)
    }

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
        )
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
