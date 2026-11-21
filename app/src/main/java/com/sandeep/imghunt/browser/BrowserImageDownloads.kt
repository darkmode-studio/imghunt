package com.sandeep.imghunt.browser

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.PhotoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object BrowserImageDownloads {
    private val queueLock=Mutex()
    internal val storageLock=Mutex()
    fun readable(context:Context,uri:String):Boolean=runCatching{
        val parsed=Uri.parse(uri)
        if(parsed.scheme=="file")File(requireNotNull(parsed.path)).isFile
        else context.contentResolver.openFileDescriptor(parsed,"r")?.use{true} ?: false
    }.getOrDefault(false)
    suspend fun enqueue(context:Context,image:PhotoItem):Long=withContext(Dispatchers.IO){queueLock.withLock{
        val dao=ImgHuntDb.get(context).dao()
        val pending=dao.pending(image.highResUrl)
        if(pending!=null)return@withLock pending.id
        val existing=dao.importedUrl(image.highResUrl)?.takeIf{readable(context,it.localUri)}
        val record=DownloadRecord(photoId=image.id,title=image.title,provider=image.sourceLabel,
            highResUrl=image.highResUrl,sourcePageUrl=image.sourcePageUrl,mimeType=existing?.mimeType ?: "image/jpeg",
            fileName="ImgHunt_${image.id}.jpg",savedUri=existing?.localUri,status=if(existing==null)"queued" else "done")
        val id=dao.insert(record)
        if(existing==null)schedule(context,id,!image.highResUrl.startsWith("file:"))
        id
    }}
    suspend fun retry(context:Context,record:DownloadRecord)=withContext(Dispatchers.IO){storageLock.withLock{
        val dao=ImgHuntDb.get(context).dao();val current=dao.getById(record.id)
        if(current!=null&&current.status=="failed"){
            dao.update(current.copy(status="queued",bytesDone=0L,error=null,workerId=null))
            schedule(context,current.id,!current.highResUrl.startsWith("file:"))
        }
    }}
    suspend fun cancel(context:Context,record:DownloadRecord)=withContext(Dispatchers.IO){storageLock.withLock{
        val dao=ImgHuntDb.get(context).dao();val current=dao.getById(record.id)
        if(current!=null&&current.status!="done"){
            dao.update(current.copy(status="failed",error="Cancelled"))
            WorkManager.getInstance(context).cancelUniqueWork("imghunt_dl_${record.id}")
        }
    }}
    private fun schedule(context:Context,id:Long,needsNetwork:Boolean){
        val request=OneTimeWorkRequestBuilder<BrowserImageWorker>().setInputData(workDataOf("record_id" to id))
            .addTag("imghunt_browser_import").addTag("imghunt_record_$id")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(needsNetwork)NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("imghunt_dl_$id",ExistingWorkPolicy.REPLACE,request)
    }
}
