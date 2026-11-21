package com.sandeep.imghunt.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: String,
    val title: String,
    val provider: String,
    val highResUrl: String,
    val sourcePageUrl: String,
    val mimeType: String,
    val fileName: String,
    val savedUri: String? = null,
    val bytesTotal: Long = 0L,
    val bytesDone: Long = 0L,
    val status: String = "queued",
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY id DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("DELETE FROM downloads WHERE status = 'done' OR status = 'failed'")
    suspend fun clearFinished()
}

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
abstract class ImgHuntDb : RoomDatabase() {
    abstract fun dao(): DownloadDao

    companion object {
        @Volatile
        private var instance: ImgHuntDb? = null

        fun get(ctx: Context): ImgHuntDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(ctx.applicationContext, ImgHuntDb::class.java, "imghunt.db")
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}
