package com.sandeep.imghunt.browser

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.*
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

class BrowserImageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = ImgHuntDb.get(applicationContext); val dao = db.dao()
        val recordId = inputData.getLong("record_id", -1L)
        val record = dao.getById(recordId) ?: return@withContext Result.failure()
        val part = File(DownloadPipeline.tempFile(applicationContext,recordId).parentFile,"$recordId-$id.part")
        try {
            dao.update(record.copy(status = "running",bytesDone = 0,error = null))
            if (record.highResUrl.startsWith("file:")) {
                val cached = BrowserImageStore.cachedFile(applicationContext,record.highResUrl)
                require(cached.length() <= 5L*1024*1024) { "Local preview is too large" }
                cached.copyTo(part,overwrite = true)
            } else {
                val url = BrowserImagePolicy.https(record.highResUrl) ?: error("Only public HTTPS images can be imported")
                val call = BrowserImageNetwork.client.newCall(Request.Builder().url(url)
                    .header("User-Agent","ImgHunt/1.2 user-selected-image").build())
                coroutineScope {
                    val watcher = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
                    try { call.execute().use { response ->
                        require(response.isSuccessful) { if (response.code in listOf(401,403)) "The site does not permit this direct download. Reopen the source; no access controls were bypassed."
                            else "Image host returned HTTP ${response.code}; its URL may have expired" }
                        val body = response.body ?: error("Image host returned an empty response")
                        val type = body.contentType()?.toString().orEmpty()
                        require(!type.startsWith("text/") && !type.contains("json")) { "This link returned a page, not an image. Open the source and scan again." }
                        val limit = 25L*1024*1024
                        require(body.contentLength() <= limit) { "Image exceeds the 25 MB import limit" }
                        var bytes = 0L; var last = 0L
                        body.byteStream().use { input -> FileOutputStream(part).use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                ensureActive(); val count = input.read(buffer); if (count < 0) break
                                bytes += count; require(bytes <= limit) { "Image exceeds the 25 MB import limit" }
                                output.write(buffer,0,count)
                                if (System.currentTimeMillis()-last > 300) {
                                    last = System.currentTimeMillis()
                                    dao.getById(recordId)?.let { dao.update(it.copy(bytesDone = bytes,bytesTotal = body.contentLength().coerceAtLeast(0))) }
                                }
                            }
                        } }
                    } } finally { watcher.cancel() }
                }
            }
            ensureActive()
            val info = BrowserImageStore.bounds(part); val mime = checkNotNull(info.outMimeType)
            val digest = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { input -> val buffer=ByteArray(65536); while(true){ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            BrowserImageDownloads.storageLock.withLock {
                ensureActive()
                withContext(NonCancellable) {
                    val prior = dao.importedHash(hash)?.takeIf { BrowserImageDownloads.readable(applicationContext,it.localUri) }
                    var created: Uri? = null
                    try {
                        val photo = PhotoItem(record.photoId,record.title,"",record.highResUrl,record.sourcePageUrl,SourceProvider.BROWSER,info.outWidth,info.outHeight,mime)
                        val name = DownloadPipeline.buildFileName(photo,mime).let { it.substringBeforeLast('.')+"_"+hash.take(10)+"."+it.substringAfterLast('.') }
                        val uri = prior?.localUri ?: DownloadPipeline.saveToGallery(applicationContext,part,name,mime).also { created=it }.toString()
                        val source = record.sourcePageUrl.substringBefore('?').substringBefore('#')
                        db.withTransaction {
                            if (prior == null) dao.storeImport(ImportedImage(hash,record.highResUrl,uri,record.title,source,info.outWidth,info.outHeight,mime))
                            val current = dao.getById(recordId) ?: error("Import record disappeared")
                            dao.update(current.copy(status="done",savedUri=uri,fileName=name,mimeType=mime,bytesDone=part.length(),bytesTotal=part.length(),error=null))
                        }
                    } catch (error: Exception) {
                        created?.let { uri -> runCatching { if(uri.scheme=="file") File(requireNotNull(uri.path)).delete() else applicationContext.contentResolver.delete(uri,null,null) } }
                        throw error
                    }
                }
            }
            Result.success()
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) {
            dao.getById(recordId)?.takeIf { it.status != "done" }?.let { dao.update(it.copy(status="failed",error=error.message?.take(220) ?: "Import failed")) }
            Result.failure()
        } finally { part.delete() }
    }
}
