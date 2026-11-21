package com.sandeep.imghunt

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.sandeep.imghunt.browser.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class BrowserCaptureTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var browser: BrowserViewModel
    @Test fun loadedEmbeddedImageMovesIntoNativeTrayWithoutAnApi() {
        val bitmap = Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        bitmap.recycle()
        val src = "data:image/png;base64," + Base64.encodeToString(bytes,Base64.NO_WRAP)
        val html = "<html><head><title>Fixture</title></head><body><img alt='Local fixture image' src='$src'></body></html>"
        rule.setContent {
            val app = LocalContext.current.applicationContext as Application
            val vm = remember { BrowserViewModel(app,SavedStateHandle()) }.also { browser = it }
            val loader = remember { BrowserImageNetwork.loader(app) }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            MaterialTheme { BrowserScreen(vm,loader,emptyList(),{},{},{},initialUrl = "about:blank",onWebViewReady = {
                it.loadDataWithBaseURL("https://images.example/",html,"text/html","UTF-8",null)
            }) }
        }
        rule.waitUntil(20000) { ::browser.isInitialized && browser.state.value.title == "Fixture" && browser.state.value.progress == 100 }
        rule.onNodeWithText("Collect loaded images").performClick()
        rule.waitUntil(15000) { browser.state.value.images.size == 1 }
        rule.onNodeWithText("Image tray (1)").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(browser.state.value.images.single().highResUrl.startsWith("file:"))
            assertFalse(browser.state.value.images.single().isOpenLicense)
        }
    }
}
