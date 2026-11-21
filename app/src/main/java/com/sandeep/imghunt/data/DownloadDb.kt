package com.sandeep.imghunt.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.SourceProvider

@Entity(tableName = "downloads")
data class DownloadRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: String, val title: String, val provider: String, val highResUrl: String,
    val sourcePageUrl: String, val mimeType: String, val fileName: String,
    val savedUri: String? = null, val bytesTotal: Long = 0L, val bytesDone: Long = 0L,
    val status: String = "queued", val error: String? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "imported_images", indices = [Index("originalUrl")])
data class ImportedImage(@PrimaryKey val hash: String, val originalUrl: String, val localUri: String,
    val title: String, val sourcePageUrl: String, val width: Int, val height: Int,
    val mimeType: String, val createdAt: Long = System.currentTimeMillis()) {
    fun photo() = PhotoItem("import_$hash", title, localUri, localUri, sourcePageUrl, SourceProvider.BROWSER,
        width, height, mimeType, false, "Local import; check original source rights")
}
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY id DESC") fun observeAll(): Flow<List<DownloadRecord>>
    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1") suspend fun getById(id: Long): DownloadRecord?
    @Query("SELECT * FROM downloads WHERE highResUrl = :url AND status IN ('queued','running') ORDER BY id DESC LIMIT 1") suspend fun pending(url: String): DownloadRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(record: DownloadRecord): Long
    @Update suspend fun update(record: DownloadRecord)
    @Query("DELETE FROM downloads WHERE status = 'done' OR status = 'failed'") suspend fun clearFinished()
    @Query("SELECT * FROM imported_images ORDER BY createdAt DESC") fun observeImported(): Flow<List<ImportedImage>>
    @Query("SELECT * FROM imported_images WHERE hash = :hash LIMIT 1") suspend fun importedHash(hash: String): ImportedImage?
    @Query("SELECT * FROM imported_images WHERE originalUrl = :url LIMIT 1") suspend fun importedUrl(url: String): ImportedImage?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun storeImport(image: ImportedImage)
}
@Database(entities = [DownloadRecord::class, ImportedImage::class], version = 2, exportSchema = false)
abstract class ImgHuntDb : RoomDatabase() {
    abstract fun dao(): DownloadDao
    companion object {
        @Volatile private var instance: ImgHuntDb? = null
        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS imported_images (hash TEXT NOT NULL PRIMARY KEY, originalUrl TEXT NOT NULL, localUri TEXT NOT NULL, title TEXT NOT NULL, sourcePageUrl TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, mimeType TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_images_originalUrl ON imported_images (originalUrl)")
            }
        }
        fun get(ctx: Context): ImgHuntDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, ImgHuntDb::class.java, "imghunt.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
