package com.sandeep.imghunt.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("imghunt_prefs", Context.MODE_PRIVATE)

    fun loadEnabledProviders(): Set<SourceProvider> {
        val raw = sp.getStringSet("enabled_providers", null)
            ?: return setOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA, SourceProvider.NASA, SourceProvider.ARCHIVE)
        val parsed = raw.mapNotNull { name -> runCatching { SourceProvider.valueOf(name) }.getOrNull() }.toSet()
        return if (parsed.isEmpty()) setOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA) else parsed
    }

    fun saveEnabledProviders(providers: Set<SourceProvider>) {
        val safe = if (providers.isEmpty()) setOf(SourceProvider.OPENVERSE) else providers
        sp.edit().putStringSet("enabled_providers", safe.map { it.name }.toSet()).apply()
    }

    fun loadKeys(): Map<SourceProvider, String> = buildMap {
        SourceProvider.entries.filter { it.requiresKey }.forEach { p ->
            val v = sp.getString("key_${p.name}", "").orEmpty()
            if (v.isNotBlank()) put(p, v)
        }
    }

    fun saveKey(provider: SourceProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) sp.edit().remove("key_${provider.name}").apply()
        else sp.edit().putString("key_${provider.name}", trimmed).apply()
    }
}

object DownloadPipeline {

    fun tempFile(context: Context, id: Long): File =
        File(File(context.cacheDir, "imghunt_parts").apply { mkdirs() }, "$id.part")

    fun extFromMime(mime: String?): String? {
        val clean = mime?.substringAfter("/")?.substringBefore(";")?.lowercase()?.trim().orEmpty()
        return when {
            clean.isEmpty() -> null
            clean == "jpeg" -> "jpg"
            clean == "svg+xml" -> null
            else -> clean
        }
    }

    fun mimeFromExt(ext: String): String = when (ext.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    fun buildFileName(item: PhotoItem, mime: String?): String {
        val ext = extFromMime(mime)
            ?: item.inferredExtension().takeIf { it in setOf("jpg", "png", "webp", "gif", "tif", "tiff", "avif") }
            ?: "jpg"
        val cleanTitle = item.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(36).ifBlank { item.id }
        val dims = if (item.width > 0 && item.height > 0) "_${item.width}x${item.height}" else ""
        return "ImgHunt_${cleanTitle}${dims}.$ext"
    }

    suspend fun enqueueHighRes(context: Context, item: PhotoItem) = withContext(Dispatchers.IO) {
        val dao = ImgHuntDb.get(context).dao()
        val initialExt = extFromMime(item.mimeType)
            ?: item.inferredExtension().takeIf { it in setOf("jpg", "png", "webp", "gif") }
            ?: "jpg"
        val mime = mimeFromExt(initialExt)
        val fileName = buildFileName(item, mime)
        val record = DownloadRecord(
            photoId = item.id,
            title = item.title,
            provider = item.provider.label,
            highResUrl = item.highResUrl,
            sourcePageUrl = item.sourcePageUrl,
            mimeType = mime,
            fileName = fileName,
            status = "queued"
        )
        val id = dao.insert(record)
        val req = OneTimeWorkRequestBuilder<HighResDownloadWorker>()
            .setInputData(workDataOf("record_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("imghunt_dl_$id", ExistingWorkPolicy.REPLACE, req)
    }

    fun retryDownload(context: Context, record: DownloadRecord) {
        val dao = ImgHuntDb.get(context).dao()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            dao.update(record.copy(status = "queued", error = null, bytesDone = 0L))
            val req = OneTimeWorkRequestBuilder<HighResDownloadWorker>()
                .setInputData(workDataOf("record_id" to record.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("imghunt_dl_${record.id}", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun cancelDownload(context: Context, record: DownloadRecord) {
        WorkManager.getInstance(context).cancelUniqueWork("imghunt_dl_${record.id}")
        tempFile(context, record.id).delete()
        val dao = ImgHuntDb.get(context).dao()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            dao.update(record.copy(status = "failed", error = "Cancelled"))
        }
    }

    fun saveToGallery(context: Context, tempFile: File, displayName: String, mimeType: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ImgHunt")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out, bufferSize = 65536) }
                } ?: error("Cannot open MediaStore stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ImgHunt").apply { mkdirs() }
            val dest = File(dir, displayName)
            tempFile.inputStream().use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out, bufferSize = 65536) }
            }
            return Uri.fromFile(dest)
        }
    }
}

class HighResDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordId = inputData.getLong("record_id", -1L)
        if (recordId <= 0L) return@withContext Result.failure()
        val dao = ImgHuntDb.get(applicationContext).dao()
        val rec = dao.getById(recordId) ?: return@withContext Result.success()
        dao.update(rec.copy(status = "running", error = null, bytesDone = 0L))
        val part = DownloadPipeline.tempFile(applicationContext, recordId)
        try {
            val conn = (URL(rec.highResUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", PhotoSources.APP_UA)
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if (rec.sourcePageUrl.startsWith("http")) setRequestProperty("Referer", rec.sourcePageUrl)
            }
            var totalBytes = 0L
            var resolvedMime = rec.mimeType
            var magicDetectedMime: String? = null
            var written = 0L
            try {
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                conn.contentType?.substringBefore(";")?.trim()?.takeIf { it.startsWith("image/") }?.let {
                    if (DownloadPipeline.extFromMime(it) != null) resolvedMime = it
                }
                val contentLen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) conn.contentLengthLong else conn.contentLength.toLong()
                if (contentLen > 0) totalBytes = contentLen
                dao.update(dao.getById(recordId)!!.copy(bytesTotal = totalBytes))

                val buf = ByteArray(65536)
                var lastReportMs = System.currentTimeMillis()
                conn.inputStream.use { input ->
                    FileOutputStream(part).use { out ->
                        while (true) {
                            if (isStopped) error("Cancelled")
                            val read = input.read(buf)
                            if (read <= 0) break
                            if (magicDetectedMime == null) {
                                magicDetectedMime = PhotoSources.sniffMimeFromBytes(buf.copyOfRange(0, read))
                            }
                            out.write(buf, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 250L) {
                                lastReportMs = now
                                dao.getById(recordId)?.let { cur ->
                                    dao.update(cur.copy(bytesDone = written, bytesTotal = if (totalBytes > 0L) totalBytes else cur.bytesTotal))
                                }
                            }
                        }
                    }
                }
                if (written <= 0L) error("Empty image stream")
                if (totalBytes <= 0L) totalBytes = written
            } finally {
                conn.disconnect()
            }

            val finalMime = magicDetectedMime ?: resolvedMime
            val finalName = DownloadPipeline.buildFileName(
                PhotoItem(rec.photoId, rec.title, "", rec.highResUrl, rec.sourcePageUrl, SourceProvider.OPENVERSE, mimeType = finalMime),
                finalMime
            )
            val savedUri = DownloadPipeline.saveToGallery(applicationContext, part, finalName, finalMime)
            part.delete()
            dao.update(
                dao.getById(recordId)!!.copy(
                    status = "done",
                    fileName = finalName,
                    mimeType = finalMime,
                    savedUri = savedUri.toString(),
                    bytesTotal = totalBytes,
                    bytesDone = totalBytes,
                    error = null
                )
            )
            Result.success()
        } catch (t: Throwable) {
            part.delete()
            dao.getById(recordId)?.let { cur ->
                dao.update(cur.copy(status = "failed", bytesDone = written, error = t.message ?: "Download failed"))
            }
            Result.failure()
        }
    }
}
