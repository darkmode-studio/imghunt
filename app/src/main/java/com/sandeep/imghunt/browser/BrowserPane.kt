package com.sandeep.imghunt.browser

import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun BrowserPane(vm: BrowserViewModel, modifier: Modifier, initialUrl: String, onReady: (WebView?) -> Unit) {
    var current by remember { mutableStateOf<WebView?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(current, owner) {
        val web = current
        val observer = LifecycleEventObserver { _, event -> runCatching {
            if (event == Lifecycle.Event.ON_PAUSE) {
                web?.saveState(Bundle().also { vm.history = it }); web?.onPause(); web?.pauseTimers()
            } else if (event == Lifecycle.Event.ON_RESUME) { web?.onResume(); web?.resumeTimers() }
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { context -> FrameLayout(context).apply {
        try {
            val web = BrowserWebView.create(context, vm) { destroyed -> if (current === destroyed) { current = null; onReady(null) } }
            addView(web, FrameLayout.LayoutParams(-1,-1)); current = web
            val restored = vm.history?.let { web.restoreState(it) } != null
            if (!restored) web.loadUrl(initialUrl)
            onReady(web)
        } catch (error: Exception) { vm.error("Android System WebView is unavailable. Update it or use Open browser.") }
    } }, modifier = modifier, onReset = null, onRelease = { container ->
        (container.getChildAt(0) as? WebView)?.let { web ->
            runCatching {
                web.saveState(Bundle().also { vm.history = it }); web.stopLoading(); web.onPause()
                web.webViewClient = WebViewClient(); web.webChromeClient = WebChromeClient()
                container.removeView(web); web.destroy()
            }
            if (current === web) { current = null; onReady(null) }
        }
    })
}
