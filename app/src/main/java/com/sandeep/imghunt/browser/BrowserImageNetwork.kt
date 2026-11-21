package com.sandeep.imghunt.browser

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object BrowserImageNetwork {
    private val publicDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !BrowserImagePolicy.publicAddress(it) })
                throw UnknownHostException("Private-network images are not supported")
            return addresses
        }
    }
    val client: OkHttpClient = OkHttpClient.Builder().dns(publicDns)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            if (BrowserImagePolicy.https(chain.request().url.toString()) == null)
                throw IOException("Only public HTTPS images are supported")
            chain.proceed(chain.request())
        }.build()
    fun loader(context: Context): ImageLoader = ImageLoader.Builder(context.applicationContext)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.build()
}
