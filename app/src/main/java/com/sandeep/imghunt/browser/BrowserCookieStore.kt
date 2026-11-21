package com.sandeep.imghunt.browser

import android.webkit.CookieManager
import android.webkit.WebView
import java.util.concurrent.Executors

/** App-local WebView cookies only. No cookie values are read, logged, exported or copied into jobs. */
object BrowserCookieStore {
    private val writer=Executors.newSingleThreadExecutor()
    fun enable(view: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
    }
    fun flush() = writer.submit { runCatching { CookieManager.getInstance().flush() }; Unit }
}
