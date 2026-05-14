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
)

@Dao
interface LikedTrackDao {
    @Insert
    suspend fun insert(track: LikedTrack): Long

    @Query("SELECT * FROM liked_tracks ORDER BY liked_at DESC")
    fun observeAll(): Flow<List<LikedTrack>>

    @Query("SELECT COUNT(*) FROM liked_tracks")
    suspend fun count(): Int

    @Query("DELETE FROM liked_tracks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [LikedTrack::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTrackDao(): LikedTrackDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checkitout.db"
                ).build().also { instance = it }
            }
    }
}
