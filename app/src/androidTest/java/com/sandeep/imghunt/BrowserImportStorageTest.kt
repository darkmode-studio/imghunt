package com.sandeep.imghunt

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.sandeep.imghunt.browser.*
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class BrowserImportStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun finished(id:Long):DownloadRecord=withTimeout(30000){
        val dao=ImgHuntDb.get(context).dao()
        while(true){val row=dao.getById(id)!!;if(row.status in setOf("done","failed"))return@withTimeout row;delay(100)}
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    @Test fun contentDeduplicationKeepsOneLibraryItem()=runBlocking{
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(UUID.randomUUID().hashCode() or 0xff000000.toInt())
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val candidate=BrowserCandidate("data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP),"https://fixture.example/import")
        val image=BrowserImageStore.capture(context,candidate)
        val first=finished(BrowserImageDownloads.enqueue(context,image));assertEquals(first.error,"done",first.status)
        val second=finished(BrowserImageDownloads.enqueue(context,image));assertEquals("done",second.status)
        assertEquals(first.savedUri,second.savedUri)
        assertEquals(1,ImgHuntDb.get(context).dao().observeImported().first().count{it.originalUrl==image.highResUrl})
    }
    @Test fun invalidImageIsNotReportedAsImported()=runBlocking{
        val file=File(BrowserImageStore.directory(context),UUID.randomUUID().toString()+".preview")
        file.writeText("<html>This is not an image</html>")
        val image=PhotoItem("browser_"+UUID.randomUUID(),"Invalid fixture","",file.toURI().toString(),"https://fixture.example/",SourceProvider.BROWSER)
        val record=finished(BrowserImageDownloads.enqueue(context,image))
        assertEquals("failed",record.status);assertNull(record.savedUri)
        assertNull(ImgHuntDb.get(context).dao().importedUrl(image.highResUrl))
        runCatching { file.delete() }
        Unit
    }
    @Test fun cancelledImportIsNotReportedAsComplete()=runBlocking{
        val image=PhotoItem("browser_"+UUID.randomUUID(),"Cancelled fixture","","https://invalid.example/"+UUID.randomUUID()+".png","https://fixture.example/",SourceProvider.BROWSER)
        val id=BrowserImageDownloads.enqueue(context,image)
        BrowserImageDownloads.cancel(context,ImgHuntDb.get(context).dao().getById(id)!!)
        val record=ImgHuntDb.get(context).dao().getById(id)!!
        assertEquals("failed",record.status);assertNull(record.savedUri)
        assertNull(ImgHuntDb.get(context).dao().importedUrl(image.highResUrl))
    }
}
