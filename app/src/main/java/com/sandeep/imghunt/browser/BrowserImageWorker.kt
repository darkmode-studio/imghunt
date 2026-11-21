package com.sandeep.imghunt.browser

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI

class BrowserImageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = ImgHuntDb.get(applicationContext).dao()
        val recordId = inputData.getLong("record_id", -1L)
        val record = dao.getById(recordId) ?: return@withContext Result.failure()
        val part = DownloadPipeline.tempFile(applicationContext, recordId)
        try {
            dao.update(record.copy(status = "running", bytesDone = 0, error = null))
            if (record.highResUrl.startsWith("file:")) {
                val cached = BrowserImageStore.cachedFile(applicationContext, record.highResUrl)
                require(cached.length() <= 5L * 1024 * 1024) { "Local preview is too large" }
                cached.copyTo(part, overwrite = true)
            } else {
                val url = BrowserImagePolicy.https(record.highResUrl) ?: error("Only public HTTPS images can be saved")
                val request = Request.Builder().url(url).header("User-Agent", "ImgHunt/1.2 user-selected-image")
                // Share the source origin only, never page query strings, browser cookies or account headers.
                runCatching { URI(record.sourcePageUrl) }.getOrNull()?.let { source ->
                    if (source.scheme == "https" && source.host != null) request.header("Referer", "https://${source.host}/")
                }
                val call = BrowserImageNetwork.client.newCall(request.build())
                coroutineScope {
                    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.execute().use { response ->
                            require(response.isSuccessful) { "Image host returned HTTP ${response.code}" }
                            val body = response.body ?: error("The image host returned an empty response")
                            val type = body.contentType()?.toString().orEmpty()
                            require(!type.startsWith("text/") && !type.contains("json")) { "This link returned a web page, not an image" }
                            val limit = 25L * 1024 * 1024
                            require(body.contentLength() <= limit) { "Image is larger than the 25 MB save limit" }
                            var total = 0L
                            var updated = 0L
                            body.byteStream().use { input -> FileOutputStream(part).use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= limit) { "Image is larger than the 25 MB save limit" }
                                    output.write(buffer, 0, count)
                                    val now = System.currentTimeMillis()
                                    if (now - updated > 300) {
                                        updated = now
                                        dao.getById(recordId)?.let { dao.update(it.copy(bytesDone = total,
                                            bytesTotal = body.contentLength().coerceAtLeast(0))) }
                                    }
                                }
                            } }
                        }
                    } finally { watcher.cancel() }
                }
            }
            ensureActive()
            val info = BrowserImageStore.bounds(part)
            val mime = checkNotNull(info.outMimeType)
            val photo = PhotoItem(record.photoId, record.title, "", record.highResUrl, record.sourcePageUrl,
                SourceProvider.BROWSER, info.outWidth, info.outHeight, mime)
            val filename = DownloadPipeline.buildFileName(photo, mime)
            val uri = DownloadPipeline.saveToGallery(applicationContext, part, filename, mime)
            val size = part.length()
            part.delete()
            dao.getById(recordId)?.let { dao.update(it.copy(status = "done", savedUri = uri.toString(),
                fileName = filename, mimeType = mime, bytesDone = size, bytesTotal = size, error = null)) }
            Result.success()
        } catch (cancel: CancellationException) {
            part.delete()
            withContext(NonCancellable) { dao.getById(recordId)?.let { dao.update(it.copy(status = "failed", error = "Cancelled")) } }
            throw cancel
        } catch (error: Exception) {
            part.delete()
            dao.getById(recordId)?.let { dao.update(it.copy(status = "failed", error = error.message?.take(160) ?: "Image could not be saved")) }
            Result.failure()
        }
    }
}
