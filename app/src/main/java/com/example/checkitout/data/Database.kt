package com.example.checkitout.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "liked_tracks")
data class LikedTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val album: String?,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "liked_at") val likedAt: Long,
    /** Identifier of the playlist this entry was filed under. "default" for inbox. */
    val playlist: String = "default",
    /**
     * Unique key for cross-device sync. Uses millisecond-precision timestamp
     * so that multiple "likes" of the same song at different times are each
     * preserved as separate log entries (different context: time, place, mood).
     */
    @ColumnInfo(name = "sync_id") val syncId: String = buildSyncId(title, artist, likedAt),

    // ---------------- LikeContext columns (v3 -> v4 migration) ----------------
    // All optional; populated asynchronously after row insertion.

    // Time
    @ColumnInfo(name = "tz_id") val tzId: String? = null,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int? = null,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int? = null,
    @ColumnInfo(name = "time_bucket") val timeBucket: String? = null,

    // Playback
    @ColumnInfo(name = "position_ms") val positionMs: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "position_pct") val positionPct: Float? = null,

    // Audio routing
    @ColumnInfo(name = "audio_output") val audioOutput: String? = null,
    @ColumnInfo(name = "bt_device_name") val btDeviceName: String? = null,

    // Location
    val lat: Double? = null,
    val lng: Double? = null,
    @ColumnInfo(name = "place_label") val placeLabel: String? = null,

    // Activity (sensor-based)
    val activity: String? = null,
    @ColumnInfo(name = "step_count") val stepCount: Int? = null,
    @ColumnInfo(name = "accel_magnitude") val accelMagnitude: Float? = null,

    // Environment
    val weather: String? = null,
    @ColumnInfo(name = "temp_c") val tempC: Float? = null,
    @ColumnInfo(name = "humidity_pct") val humidityPct: Float? = null,

    // Spotify audio features
    @ColumnInfo(name = "spotify_id") val spotifyId: String? = null,
    val bpm: Float? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    val acousticness: Float? = null,
    val instrumentalness: Float? = null,
    @ColumnInfo(name = "music_key") val musicKey: Int? = null,
    val loudness: Float? = null,

    // Lyrics snapshot
    @ColumnInfo(name = "lyrics_snippet") val lyricsSnippet: String? = null,
) {
    companion object {
        fun buildSyncId(title: String, artist: String?, likedAt: Long): String {
            return "${title.trim().lowercase()}|${artist?.trim()?.lowercase().orEmpty()}|$likedAt"
        }
    }
}

@Dao
interface LikedTrackDao {
    @Insert
    suspend fun insert(track: LikedTrack): Long

    @Query("SELECT * FROM liked_tracks ORDER BY liked_at DESC")
    fun observeAll(): Flow<List<LikedTrack>>

    @Query("SELECT * FROM liked_tracks ORDER BY liked_at DESC")
    suspend fun getAll(): List<LikedTrack>

    @Query("SELECT sync_id FROM liked_tracks")
    suspend fun allSyncIds(): List<String>

    @Query("SELECT COUNT(*) FROM liked_tracks")
    suspend fun count(): Int

    @Query("DELETE FROM liked_tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM liked_tracks WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    /** Attach a fully collected LikeContext to an existing row. */
    @Query("""
        UPDATE liked_tracks SET
            tz_id = :tzId,
            day_of_week = :dayOfWeek,
            hour_of_day = :hourOfDay,
            time_bucket = :timeBucket,
            position_ms = :positionMs,
            duration_ms = :durationMs,
            position_pct = :positionPct,
            audio_output = :audioOutput,
            bt_device_name = :btDeviceName,
            lat = :lat,
            lng = :lng,
            place_label = :placeLabel,
            activity = :activity,
            step_count = :stepCount,
            accel_magnitude = :accelMagnitude,
            weather = :weather,
            temp_c = :tempC,
            humidity_pct = :humidityPct,
            spotify_id = :spotifyId,
            bpm = :bpm,
            energy = :energy,
            valence = :valence,
            danceability = :danceability,
            acousticness = :acousticness,
            instrumentalness = :instrumentalness,
            music_key = :musicKey,
            loudness = :loudness,
            lyrics_snippet = :lyricsSnippet
        WHERE id = :id
    """)
    suspend fun attachContext(
        id: Long,
        tzId: String?,
        dayOfWeek: Int?,
        hourOfDay: Int?,
        timeBucket: String?,
        positionMs: Long?,
        durationMs: Long?,
        positionPct: Float?,
        audioOutput: String?,
        btDeviceName: String?,
        lat: Double?,
        lng: Double?,
        placeLabel: String?,
        activity: String?,
        stepCount: Int?,
        accelMagnitude: Float?,
        weather: String?,
        tempC: Float?,
        humidityPct: Float?,
        spotifyId: String?,
        bpm: Float?,
        energy: Float?,
        valence: Float?,
        danceability: Float?,
        acousticness: Float?,
        instrumentalness: Float?,
        musicKey: Int?,
        loudness: Float?,
        lyricsSnippet: String?,
    )
}

@Database(entities = [LikedTrack::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTrackDao(): LikedTrackDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE liked_tracks ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE liked_tracks SET sync_id =
                        LOWER(TRIM(title)) || '|' || COALESCE(LOWER(TRIM(artist)),'') || '|' || CAST(liked_at / 60000 AS TEXT)
                    WHERE sync_id = ''
                """.trimIndent())
            }
        }

        /** Migrate sync_id from minute-bucket to millisecond precision so each
         *  "like" event is preserved individually even for the same song. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE liked_tracks SET sync_id =
                        LOWER(TRIM(title)) || '|' || COALESCE(LOWER(TRIM(artist)),'') || '|' || CAST(liked_at AS TEXT)
                """.trimIndent())
            }
        }

        /** Add LikeContext columns. All nullable, populated asynchronously after insert. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cols = listOf(
                    "tz_id TEXT",
                    "day_of_week INTEGER",
                    "hour_of_day INTEGER",
                    "time_bucket TEXT",
                    "position_ms INTEGER",
                    "duration_ms INTEGER",
                    "position_pct REAL",
                    "audio_output TEXT",
                    "bt_device_name TEXT",
                    "lat REAL",
                    "lng REAL",
                    "place_label TEXT",
                    "activity TEXT",
                    "step_count INTEGER",
                    "accel_magnitude REAL",
                    "weather TEXT",
                    "temp_c REAL",
                    "humidity_pct REAL",
                    "spotify_id TEXT",
                    "bpm REAL",
                    "energy REAL",
                    "valence REAL",
                    "danceability REAL",
                    "acousticness REAL",
                    "instrumentalness REAL",
                    "music_key INTEGER",
                    "loudness REAL",
                    "lyrics_snippet TEXT",
                )
                cols.forEach { db.execSQL("ALTER TABLE liked_tracks ADD COLUMN $it") }
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checkitout.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
