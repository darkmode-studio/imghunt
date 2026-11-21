package com.sandeep.imghunt.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SearchHttpException(val status: Int) : IOException("HTTP $status")

object SearchHttp {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", PhotoSources.APP_UA).apply {
                    headers.forEach { (key, value) -> header(key, value) }
                }.build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use { res ->
                            if (!res.isSuccessful) throw SearchHttpException(res.code)
                            val body = res.body ?: throw IOException("Empty response")
                            body.byteStream().use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (output.size() + read > 4 * 1024 * 1024) throw IOException("Search response too large")
                                    output.write(buffer, 0, read)
                                }
                                output.toString("UTF-8")
                            }
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
}
