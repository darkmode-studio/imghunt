package com.sandeep.imghunt.browser

import android.content.Context
import androidx.work.*
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BrowserImageDownloads {
    suspend fun enqueue(context: Context, image: PhotoItem) = withContext(Dispatchers.IO) {
        val record = DownloadRecord(photoId = image.id, title = image.title, provider = image.sourceLabel,
            highResUrl = image.highResUrl, sourcePageUrl = image.sourcePageUrl, mimeType = "image/jpeg",
            fileName = "ImgHunt_${image.id}.jpg")
        schedule(context, ImgHuntDb.get(context).dao().insert(record))
    }
    suspend fun retry(context: Context, record: DownloadRecord) = withContext(Dispatchers.IO) {
        ImgHuntDb.get(context).dao().update(record.copy(status = "queued", bytesDone = 0L, error = null))
        schedule(context, record.id)
    }
    private fun schedule(context: Context, id: Long) {
        val request = OneTimeWorkRequestBuilder<BrowserImageWorker>()
            .setInputData(workDataOf("record_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("imghunt_dl_$id", ExistingWorkPolicy.REPLACE, request)
    }
}
