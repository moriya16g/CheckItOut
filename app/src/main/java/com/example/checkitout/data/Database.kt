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
}

@Database(entities = [LikedTrack::class], version = 3, exportSchema = false)
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checkitout.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
