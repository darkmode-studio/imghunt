package com.sandeep.imghunt.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.*
import android.view.ViewGroup
import android.webkit.*

object BrowserWebView {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context:Context,vm:BrowserViewModel,onDestroyed:(WebView)->Unit):WebView=WebView(context).apply {
        settings.javaScriptEnabled=true
        settings.domStorageEnabled=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically=false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture=true
        settings.setGeolocationEnabled(false)
        if(Build.VERSION.SDK_INT>=26)settings.safeBrowsingEnabled=true
        BrowserCookieStore.enable(this)
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                val url=request.url.toString()
                if(url=="about:blank"||BrowserImagePolicy.https(url)!=null)return false
                vm.message("Only public HTTPS pages open here. App links are not launched automatically.")
                return true
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?){
                vm.error(null);vm.loading(0);vm.page(url,view.title,view.canGoBack(),view.canGoForward())
            }
            override fun onPageFinished(view:WebView,url:String?){
                vm.loading(100);vm.page(url,view.title,view.canGoBack(),view.canGoForward())
            }
            override fun doUpdateVisitedHistory(view:WebView,url:String?,isReload:Boolean){
                // SPA history changes do not necessarily trigger onPageFinished or a new title.
                vm.page(url,view.title,view.canGoBack(),view.canGoForward())
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
                if(request.isForMainFrame){vm.loading(100);vm.error("Page could not load. Check your connection or use the system browser.")}
            }
            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){
                if(request.isForMainFrame)vm.error("Website returned HTTP ${response.statusCode}. Try the system browser.")
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){
                handler.cancel();vm.error("Secure connection failed. Certificate errors are not bypassed.")
            }
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{
                (view.parent as? ViewGroup)?.removeView(view);view.destroy();vm.history=null;onDestroyed(view)
                vm.error("Browser stopped. Tap Reload to start it again.");return true
            }
        }
        webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,progress:Int){vm.loading(progress)}
            override fun onReceivedTitle(view:WebView,title:String?){vm.page(view.url,title,view.canGoBack(),view.canGoForward())}
            override fun onPermissionRequest(request:PermissionRequest){request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String?,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false)}
        }
        setDownloadListener{_,_,_,_,_->vm.message("Inspect an image with Find Images before importing it.")}
        setOnLongClickListener{pressed->
            val view=pressed as WebView;val hit=view.hitTestResult
            if(hit.type!=WebView.HitTestResult.IMAGE_TYPE&&hit.type!=WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)return@setOnLongClickListener false
            val page=view.url ?: return@setOnLongClickListener false
            val title=view.title ?: "Selected image"
            val fallback=if(hit.type==WebView.HitTestResult.IMAGE_TYPE)hit.extra.orEmpty()else ""
            val handler=Handler(Looper.getMainLooper()){message->
                if(view.isAttachedToWindow&&view.url==page){
                    val src=message.data.getString("src").orEmpty().ifBlank{fallback}
                    if(src.isNotBlank())vm.select(BrowserCandidate(src,page,title,message.data.getString("url").orEmpty()))
                    else vm.message("Open the image preview or source page, then long-press again.")
                }
                true
            }
            view.requestFocusNodeHref(handler.obtainMessage());true
        }
    }
}
