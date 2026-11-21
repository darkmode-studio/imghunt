package com.sandeep.imghunt.engine

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PhotoSources {

    const val APP_UA = "ImgHunt/1.0 (Android; darkmode-studio/imghunt)"

    fun createImageLoader(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", APP_UA)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()
        return ImageLoader.Builder(context.applicationContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .build()
    }

    private fun getJson(urlStr: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 10000): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", APP_UA)
            setRequestProperty("Accept", "application/json, */*;q=0.8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(q: String): String = URLEncoder.encode(q.trim(), "UTF-8")

    private fun queryVariants(query: String): List<String> {
        val trimmed = query.trim()
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = LinkedHashSet<String>()
        out += trimmed
        if (parts.size >= 2) {
            out += parts.first()
        }
        return out.toList()
    }

    fun fetchProvider(provider: SourceProvider, query: String, apiKey: String? = null): List<PhotoItem> {
        if (provider.requiresKey && apiKey.isNullOrBlank()) error("API key required for ${provider.label}")
        val q = enc(query)
        return when (provider) {
            SourceProvider.OPENVERSE -> {
                val base = "https://api.openverse.org"
                val raw = getJson("$base/v1/images/?q=$q&page_size=40")
                val arr = JSONObject(raw).optJSONArray("results") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val full = o.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val thumb = o.optString("thumbnail").takeIf { it.startsWith("http") } ?: full
                        add(PhotoItem("ov_${o.optString("id", "$i")}", o.optString("title").ifBlank { "Openverse Image" }, thumb, full, o.optString("foreign_landing_url"), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, o.optString("license").uppercase().ifBlank { "CC" }))
                    }
                }
            }
            SourceProvider.WIKIMEDIA -> {
                val base = "https://commons.wikimedia.org/w/api.php"
                val raw = getJson("$base?action=query&format=json&origin=*&generator=search&gsrnamespace=6&gsrlimit=40&gsrsearch=$q&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=640")
                val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
                buildList {
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val p = pages.optJSONObject(k) ?: continue
                        val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                        val full = info.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val thumb = info.optString("thumburl").takeIf { it.startsWith("http") } ?: full
                        val mime = info.optString("mime").ifBlank { null }
                        if (mime == "image/svg+xml") continue
                        add(PhotoItem("wm_$k", p.optString("title").removePrefix("File:").ifBlank { "Wikimedia Image" }, thumb, full, info.optString("descriptionurl"), provider, info.optInt("width", 0), info.optInt("height", 0), mime, true, "Wikimedia"))
                    }
                }
            }
            SourceProvider.ARCHIVE -> {
                val base = "https://archive.org/advancedsearch.php"
                val raw = getJson("$base?q=${enc("mediatype:image AND ($query)")}&fl[]=identifier,title&rows=36&page=1&output=json")
                val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
                buildList {
                    for (i in 0 until docs.length()) {
                        val d = docs.optJSONObject(i) ?: continue
                        val id = d.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                        val img = "https://archive.org/services/img/$id"
                        add(PhotoItem("ia_$id", d.optString("title").ifBlank { id }, img, img, "https://archive.org/details/$id", provider, 0, 0, null, true, "Archive"))
                    }
                }
            }
            SourceProvider.NASA -> {
                val base = "https://images-api.nasa.gov/search"
                val raw = getJson("$base?q=$q&media_type=image&page_size=36")
                val items = JSONObject(raw).optJSONObject("collection")?.optJSONArray("items") ?: JSONArray()
                buildList {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val data = it.optJSONArray("data")?.optJSONObject(0) ?: continue
                        val href = it.optJSONArray("links")?.optJSONObject(0)?.optString("href")?.takeIf { u -> u.startsWith("http") } ?: continue
                        val nid = data.optString("nasa_id").ifBlank { "$i" }
                        add(PhotoItem("nasa_$nid", data.optString("title").ifBlank { nid }, href, href, "https://images.nasa.gov/details/$nid", provider, 0, 0, null, true, "NASA"))
                    }
                }
            }
            SourceProvider.MET -> {
                val base = "https://collectionapi.metmuseum.org/public/collection/v1"
                val raw = getJson("$base/search?hasImages=true&q=$q")
                val ids = JSONObject(raw).optJSONArray("objectIDs") ?: JSONArray()
                buildList {
                    for (i in 0 until minOf(ids.length(), 14)) {
                        val id = ids.optInt(i, -1)
                        if (id <= 0) continue
                        val objRaw = runCatching { getJson("$base/objects/$id", timeoutMs = 4500) }.getOrNull() ?: continue
                        val obj = JSONObject(objRaw)
                        val full = obj.optString("primaryImage").takeIf { it.startsWith("http") } ?: continue
                        val thumb = obj.optString("primaryImageSmall").takeIf { it.startsWith("http") } ?: full
                        val open = obj.optBoolean("isPublicDomain", false)
                        add(PhotoItem("met_$id", obj.optString("title").ifBlank { "Met #$id" }, thumb, full, obj.optString("objectURL"), provider, 0, 0, null, open, if (open) "Met Open" else "Met"))
                    }
                }
            }
            SourceProvider.UNSPLASH -> {
                val base = "https://api.unsplash.com/search/photos"
                val raw = getJson("$base?query=$q&per_page=30", mapOf("Authorization" to "Client-ID ${apiKey.orEmpty()}"))
                val arr = JSONObject(raw).optJSONArray("results") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val urls = o.optJSONObject("urls") ?: continue
                        val full = (urls.optString("raw").takeIf { it.isNotBlank() } ?: urls.optString("full")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = urls.optString("small").takeIf { it.startsWith("http") } ?: full
                        add(PhotoItem("us_${o.optString("id", "$i")}", o.optString("alt_description").ifBlank { "Unsplash Photo" }, thumb, full, o.optJSONObject("links")?.optString("html").orEmpty(), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Unsplash"))
                    }
                }
            }
            SourceProvider.PEXELS -> {
                val base = "https://api.pexels.com/v1/search"
                val raw = getJson("$base?query=$q&per_page=30", mapOf("Authorization" to apiKey.orEmpty()))
                val arr = JSONObject(raw).optJSONArray("photos") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val src = o.optJSONObject("src") ?: continue
                        val full = (src.optString("original").takeIf { it.isNotBlank() } ?: src.optString("large2x")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = src.optString("medium").takeIf { it.startsWith("http") } ?: full
                        add(PhotoItem("px_${o.optInt("id", i)}", o.optString("alt").ifBlank { "Pexels Photo" }, thumb, full, o.optString("url"), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Pexels"))
                    }
                }
            }
            SourceProvider.PIXABAY -> {
                val base = "https://pixabay.com/api/"
                val raw = getJson("$base?key=${enc(apiKey.orEmpty())}&q=$q&image_type=photo&per_page=30")
                val arr = JSONObject(raw).optJSONArray("hits") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val full = (o.optString("largeImageURL").takeIf { it.isNotBlank() } ?: o.optString("webformatURL")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = o.optString("previewURL").takeIf { it.startsWith("http") } ?: full
                        add(PhotoItem("pb_${o.optInt("id", i)}", o.optString("tags").ifBlank { "Pixabay Photo" }, thumb, full, o.optString("pageURL"), provider, o.optInt("imageWidth", 0), o.optInt("imageHeight", 0), null, true, "Pixabay"))
                    }
                }
            }
        }
    }

    suspend fun searchWithFallback(
        query: String,
        filters: SearchFilters,
        enabledProviders: Set<SourceProvider>,
        apiKeys: Map<SourceProvider, String>
    ): Pair<List<PhotoItem>, List<ProviderResult>> = coroutineScope {
        val active = enabledProviders.filter { !it.requiresKey || !apiKeys[it].isNullOrBlank() }
        val primaryList = if (active.isNotEmpty()) active else listOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA)
        val queries = queryVariants(query)

        val statuses = primaryList.map { p ->
            async(Dispatchers.IO) {
                val mergedItems = LinkedHashMap<String, PhotoItem>()
                var lastErr: String? = null
                for (qv in queries) {
                    val r = withTimeoutOrNull(10000L) { runCatching { fetchProvider(p, qv, apiKeys[p]) } }
                    when {
                        r == null -> lastErr = "Timed out"
                        r.isFailure -> lastErr = r.exceptionOrNull()?.message ?: "Error"
                        else -> r.getOrDefault(emptyList()).forEach { mergedItems.putIfAbsent(it.id, it) }
                    }
                    if (mergedItems.size >= 24) break
                }
                if (mergedItems.isNotEmpty()) ProviderResult(p, mergedItems.values.toList(), null)
                else ProviderResult(p, emptyList(), lastErr)
            }
        }.awaitAll().toMutableList()

        val deduped = LinkedHashMap<String, PhotoItem>()
        for (st in statuses) {
            for (item in st.items) {
                if (filters.matches(item)) deduped.putIfAbsent(item.highResUrl, item)
            }
        }

        if (deduped.size < 10) {
            val fallbacks = SourceProvider.entries.filter { it !in primaryList && (!it.requiresKey || !apiKeys[it].isNullOrBlank()) }
            if (fallbacks.isNotEmpty()) {
                val fbList = fallbacks.map { fb ->
                    async(Dispatchers.IO) {
                        val r = withTimeoutOrNull(10000L) { runCatching { fetchProvider(fb, queries.first(), apiKeys[fb]) } }
                        when {
                            r == null -> ProviderResult(fb, emptyList(), "Timed out", usedFallback = true)
                            r.isFailure -> ProviderResult(fb, emptyList(), r.exceptionOrNull()?.message ?: "Error", usedFallback = true)
                            else -> ProviderResult(fb, r.getOrDefault(emptyList()), usedFallback = true)
                        }
                    }
                }.awaitAll()
                for (fr in fbList) {
                    statuses += fr
                    for (item in fr.items) {
                        if (filters.matches(item)) deduped.putIfAbsent(item.highResUrl, item)
                    }
                }
            }
        }

        withContext(Dispatchers.Default) { deduped.values.toList() } to statuses
    }
}
