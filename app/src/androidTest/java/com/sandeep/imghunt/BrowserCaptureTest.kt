package com.sandeep.imghunt

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.sandeep.imghunt.browser.*
import com.sandeep.imghunt.data.ImgHuntDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BrowserCaptureTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var browser:BrowserViewModel
    private lateinit var web:WebView
    private fun render(html:String){
        rule.setContent{
            val app=LocalContext.current.applicationContext as Application
            val vm=remember{BrowserViewModel(app,SavedStateHandle())}.also{browser=it}
            val loader=remember{BrowserImageNetwork.loader(app)}
            DisposableEffect(loader){onDispose{loader.shutdown()}}
            MaterialTheme{BrowserScreen(vm,loader,emptyList(),{},{},{},initialUrl="about:blank",onWebViewReady={
                web=it;it.settings.blockNetworkLoads=true
                it.loadDataWithBaseURL("https://fixture.example/page",html,"text/html","UTF-8",null)
            })}
        }
        rule.waitUntil(20000){::browser.isInitialized&&browser.state.value.title=="Fixture"&&browser.state.value.progress==100}
    }
    @Test fun selectedEmbeddedImageIsImportedOnlyAfterARealLocalSave(){
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff347b86.toInt())
        val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val src="data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
        render("<html><head><title>Fixture</title></head><body><img alt='Local fixture image' src='$src'></body></html>")
        rule.onNodeWithText("Find Images").performClick()
        rule.waitUntil(15000){browser.state.value.images.size==1&&!browser.state.value.collecting}
        rule.onNodeWithText("Images from this page").assertIsDisplayed()
        rule.onNodeWithText("Preview only").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        rule.onNodeWithText("Import to ImgHunt (1)").performClick()
        rule.onNodeWithText("Import previews too").performClick()
        val photo=browser.state.value.images.single()
        rule.waitUntil(30000){browser.state.value.transfers[photo.id]?.status in setOf("done","failed")}
        val transfer=browser.state.value.transfers[photo.id]!!
        assertEquals(transfer.error,"done",transfer.status)
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val stored=runBlocking{ImgHuntDb.get(app).dao().importedUrl(photo.highResUrl)}
        assertNotNull(stored);assertEquals(128,stored!!.width);assertEquals(128,stored.height)
        assertEquals("image/png",stored.mimeType);assertTrue(BrowserImageDownloads.readable(app,stored.localUri))
        assertFalse(photo.isOpenLicense)
    }
    @Test fun responsiveLazyAndCssImagesAreDetectedWithoutNetworkRequests(){
        render("""<html><head><title>Fixture</title></head><body>
            <img alt='Responsive' style='width:240px;height:180px' src='https://fixture.example/thumb.jpg' srcset='https://fixture.example/medium.jpg 640w, https://fixture.example/full.jpg 2048w'>
            <img alt='Lazy' style='width:200px;height:160px' data-src='https://fixture.example/lazy.jpg'>
            <div aria-label='Background' style='width:200px;height:160px;background-image:url(https://fixture.example/background.jpg)'></div>
            <img src='https://fixture.example/pixel.png' width='1' height='1'>
            </body></html>""")
        rule.onNodeWithText("Find Images").performClick()
        rule.waitUntil(15000){!browser.state.value.collecting&&browser.state.value.items.size==3}
        val images=browser.state.value.images
        assertTrue(images.any{it.highResUrl=="https://fixture.example/full.jpg"})
        assertTrue(images.any{it.highResUrl=="https://fixture.example/lazy.jpg"})
        assertTrue(images.any{it.highResUrl=="https://fixture.example/background.jpg"})
        assertFalse(images.any{it.highResUrl.endsWith("pixel.png")})
    }
    @Test fun browserControlsRemainVisibleAndSpaHistoryIsTracked(){
        render("<html><head><title>Fixture</title></head><body>Navigation fixture</body></html>")
        rule.onNodeWithContentDescription("Browser home").assertIsDisplayed()
        rule.onNodeWithText("Search images or enter website address").performClick()
        rule.onNodeWithText("Search images or enter website address").performTextInput("mountains")
        rule.onNodeWithText("Find Images").assertIsDisplayed()
        rule.runOnIdle{web.evaluateJavascript("history.pushState({},'', '/second')",null)}
        rule.waitUntil(15000){browser.state.value.pageUrl.endsWith("/second")}
        if(browser.state.value.canBack){
            rule.runOnIdle{web.goBack()}
            rule.waitUntil(15000){!browser.state.value.pageUrl.endsWith("/second")}
        }
    }
    @Test fun appLocalPersistentCookiesSurviveBrowserRecreation(){
        render("<html><head><title>Fixture</title></head><body>Cookie fixture</body></html>")
        val latch=CountDownLatch(1);val accepted=AtomicBoolean(false)
        rule.runOnIdle{CookieManager.getInstance().setCookie("https://fixture.example/","imghunt_fixture=1; Max-Age=3600; Secure; SameSite=Lax"){
            accepted.set(it);latch.countDown()
        }}
        assertTrue(latch.await(10,TimeUnit.SECONDS));assertTrue(accepted.get())
        BrowserCookieStore.flush().get(10,TimeUnit.SECONDS)
        rule.runOnIdle{
            val other=BrowserWebView.create(web.context,browser,{})
            BrowserCookieStore.enable(other)
            assertTrue(CookieManager.getInstance().getCookie("https://fixture.example/").orEmpty().contains("imghunt_fixture=1"))
            other.destroy()
        }
    }
}
