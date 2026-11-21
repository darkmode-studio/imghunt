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
import java.util.concurrent.Executors

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

    fun sniffMimeFromBytes(bytes: ByteArray): String? {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "image/jpeg"
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
        if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) return "image/webp"
        if (bytes.size >= 6) {
            val sig = String(bytes, 0, 6, Charsets.ISO_8859_1)
            if (sig == "GIF87a" || sig == "GIF89a") return "image/gif"
        }
        return null
    }

    private fun getJson(urlStr: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8500): String {
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

    fun queryVariants(query: String): List<String> {
        val trimmed = query.trim()
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = LinkedHashSet<String>()
        out += trimmed
        if (parts.size >= 2) {
            out += parts.first()
            out += parts.last()
        }
        return out.toList()
    }

    private fun endpoint(provider: SourceProvider, q: String, rawQuery: String, page: Int, key: String?): Pair<String, Map<String, String>> {
        val p1 = page + 1
        return when (provider) {
            SourceProvider.OPENVERSE ->
                "https://api.openverse.org/v1/images/?q=$q&page=$p1&page_size=30" to emptyMap()
            SourceProvider.WIKIMEDIA ->
                "https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrnamespace=6&gsrlimit=30&gsroffset=${page * 30}&gsrsearch=$q&prop=imageinfo&iiprop=url|size|mime|extmetadata&iiurlwidth=640" to emptyMap()
            SourceProvider.ARCHIVE ->
                "https://archive.org/advancedsearch.php?q=${enc("mediatype:image AND ($rawQuery)")}&fl[]=identifier,title,creator,licenseurl&rows=30&page=$p1&output=json" to emptyMap()
            SourceProvider.NASA ->
                "https://images-api.nasa.gov/search?q=$q&media_type=image&page=$p1&page_size=30" to emptyMap()
            SourceProvider.MET ->
                "https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&q=$q" to emptyMap()
            SourceProvider.UNSPLASH ->
                "https://api.unsplash.com/search/photos?query=$q&page=$p1&per_page=30" to mapOf("Authorization" to "Client-ID ${key.orEmpty()}")
            SourceProvider.PEXELS ->
                "https://api.pexels.com/v1/search?query=$q&page=$p1&per_page=30" to mapOf("Authorization" to key.orEmpty())
            SourceProvider.PIXABAY ->
                "https://pixabay.com/api/?key=${enc(key.orEmpty())}&q=$q&image_type=photo&page=$p1&per_page=30" to emptyMap()
        }
    }

    fun fetchProvider(provider: SourceProvider, query: String, page: Int = 0, apiKey: String? = null): List<PhotoItem> {
        if (provider.requiresKey && apiKey.isNullOrBlank()) error("API key required for ${provider.label}")
        val (url, headers) = endpoint(provider, enc(query), query, page, apiKey)
        val raw = getJson(url, headers)
        return when (provider) {
            SourceProvider.OPENVERSE -> {
                val arr = JSONObject(raw).optJSONArray("results") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val full = o.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val thumb = o.optString("thumbnail").takeIf { it.startsWith("http") } ?: full
                        val lic = o.optString("license").uppercase().ifBlank { "CC" }
                        val creator = o.optString("creator").takeIf { it.isNotBlank() }
                        add(PhotoItem("ov_${o.optString("id", "${page}_$i")}", o.optString("title").ifBlank { "Openverse Image" }, thumb, full, o.optString("foreign_landing_url"), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, lic, creator))
                    }
                }
            }
            SourceProvider.WIKIMEDIA -> {
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
                        val artist = info.optJSONObject("extmetadata")?.optJSONObject("Artist")?.optString("value")
                            ?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf { it.isNotBlank() }
                        add(PhotoItem("wm_${page}_$k", p.optString("title").removePrefix("File:").ifBlank { "Wikimedia Image" }, thumb, full, info.optString("descriptionurl"), provider, info.optInt("width", 0), info.optInt("height", 0), mime, true, "Wikimedia CC", artist))
                    }
                }
            }
            SourceProvider.ARCHIVE -> {
                val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
                buildList {
                    for (i in 0 until docs.length()) {
                        val d = docs.optJSONObject(i) ?: continue
                        val id = d.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                        val img = "https://archive.org/services/img/$id"
                        val creator = d.optString("creator").takeIf { it.isNotBlank() }
                        add(PhotoItem("ia_${page}_$id", d.optString("title").ifBlank { id }, img, img, "https://archive.org/details/$id", provider, 0, 0, null, true, "Archive", creator))
                    }
                }
            }
            SourceProvider.NASA -> {
                val items = JSONObject(raw).optJSONObject("collection")?.optJSONArray("items") ?: JSONArray()
                buildList {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val data = it.optJSONArray("data")?.optJSONObject(0) ?: continue
                        val href = it.optJSONArray("links")?.optJSONObject(0)?.optString("href")?.takeIf { u -> u.startsWith("http") } ?: continue
                        val nid = data.optString("nasa_id").ifBlank { "${page}_$i" }
                        val creator = data.optString("center").takeIf { it.isNotBlank() }
                        add(PhotoItem("nasa_${page}_$nid", data.optString("title").ifBlank { nid }, href, href, "https://images.nasa.gov/details/$nid", provider, 0, 0, null, true, "NASA Public", creator))
                    }
                }
            }
            SourceProvider.MET -> {
                val ids = JSONObject(raw).optJSONArray("objectIDs") ?: JSONArray()
                val slice = ((page * 8) until minOf(ids.length(), (page + 1) * 8)).mapNotNull { idx -> ids.optInt(idx, -1).takeIf { it > 0 } }
                if (slice.isEmpty()) return emptyList()
                val pool = Executors.newFixedThreadPool(4)
                try {
                    val futures = slice.map { id ->
                        pool.submit<PhotoItem?> {
                            runCatching {
                                val objRaw = getJson("https://collectionapi.metmuseum.org/public/collection/v1/objects/$id", timeoutMs = 3000)
                                val obj = JSONObject(objRaw)
                                val full = obj.optString("primaryImage").takeIf { it.startsWith("http") } ?: return@runCatching null
                                val thumb = obj.optString("primaryImageSmall").takeIf { it.startsWith("http") } ?: full
                                val open = obj.optBoolean("isPublicDomain", false)
                                val artist = obj.optString("artistDisplayName").takeIf { it.isNotBlank() }
                                PhotoItem("met_${page}_$id", obj.optString("title").ifBlank { "Met #$id" }, thumb, full, obj.optString("objectURL"), provider, 0, 0, null, open, if (open) "Met Open" else "Met", artist)
                            }.getOrNull()
                        }
                    }
                    futures.mapNotNull { runCatching { it.get(3200, TimeUnit.MILLISECONDS) }.getOrNull() }
                } finally {
                    pool.shutdownNow()
                }
            }
            SourceProvider.UNSPLASH -> {
                val arr = JSONObject(raw).optJSONArray("results") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val urls = o.optJSONObject("urls") ?: continue
                        val full = (urls.optString("raw").takeIf { it.isNotBlank() } ?: urls.optString("full")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = urls.optString("small").takeIf { it.startsWith("http") } ?: full
                        val creator = o.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() }
                        add(PhotoItem("us_${page}_${o.optString("id", "$i")}", o.optString("alt_description").ifBlank { "Unsplash Photo" }, thumb, full, o.optJSONObject("links")?.optString("html").orEmpty(), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Unsplash", creator))
                    }
                }
            }
            SourceProvider.PEXELS -> {
                val arr = JSONObject(raw).optJSONArray("photos") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val src = o.optJSONObject("src") ?: continue
                        val full = (src.optString("original").takeIf { it.isNotBlank() } ?: src.optString("large2x")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = src.optString("medium").takeIf { it.startsWith("http") } ?: full
                        val creator = o.optString("photographer").takeIf { it.isNotBlank() }
                        add(PhotoItem("px_${page}_${o.optInt("id", i)}", o.optString("alt").ifBlank { "Pexels Photo" }, thumb, full, o.optString("url"), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Pexels", creator))
                    }
                }
            }
            SourceProvider.PIXABAY -> {
                val arr = JSONObject(raw).optJSONArray("hits") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val full = (o.optString("largeImageURL").takeIf { it.isNotBlank() } ?: o.optString("webformatURL")).takeIf { it.startsWith("http") } ?: continue
                        val thumb = o.optString("previewURL").takeIf { it.startsWith("http") } ?: full
                        val creator = o.optString("user").takeIf { it.isNotBlank() }
                        add(PhotoItem("pb_${page}_${o.optInt("id", i)}", o.optString("tags").ifBlank { "Pixabay Photo" }, thumb, full, o.optString("pageURL"), provider, o.optInt("imageWidth", 0), o.optInt("imageHeight", 0), null, true, "Pixabay", creator))
                    }
                }
            }
        }
    }

    suspend fun searchWithFallback(
        query: String,
        filters: SearchFilters,
        page: Int,
        enabledProviders: Set<SourceProvider>,
        apiKeys: Map<SourceProvider, String>
    ): Pair<List<PhotoItem>, List<ProviderResult>> = coroutineScope {
        val active = enabledProviders.filter { !it.requiresKey || !apiKeys[it].isNullOrBlank() }
        val primaryList = if (active.isNotEmpty()) active else listOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA)
        val variants = queryVariants(query)

        val statuses = primaryList.map { p ->
            async(Dispatchers.IO) {
                val mergedItems = LinkedHashMap<String, PhotoItem>()
                var lastErr: String? = null
                for (qv in variants) {
                    val r = withTimeoutOrNull(8500L) { runCatching { fetchProvider(p, qv, page, apiKeys[p]) } }
                    when {
                        r == null -> lastErr = "Timed out"
                        r.isFailure -> lastErr = r.exceptionOrNull()?.message ?: "Error"
                        else -> r.getOrDefault(emptyList()).forEach { mergedItems.putIfAbsent(it.highResUrl, it) }
                    }
                    if (mergedItems.size >= 20) break
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

        if (deduped.size < 16) {
            val fallbacks = SourceProvider.entries.filter { it !in primaryList && (!it.requiresKey || !apiKeys[it].isNullOrBlank()) }
            if (fallbacks.isNotEmpty()) {
                val fbList = fallbacks.map { fb ->
                    async(Dispatchers.IO) {
                        val fbItems = LinkedHashMap<String, PhotoItem>()
                        var fbErr: String? = null
                        for (qv in variants) {
                            val r = withTimeoutOrNull(8500L) { runCatching { fetchProvider(fb, qv, page, apiKeys[fb]) } }
                            when {
                                r == null -> fbErr = "Timed out"
                                r.isFailure -> fbErr = r.exceptionOrNull()?.message ?: "Error"
                                else -> r.getOrDefault(emptyList()).forEach { fbItems.putIfAbsent(it.highResUrl, it) }
                            }
                            if (fbItems.size >= 14) break
                        }
                        ProviderResult(fb, fbItems.values.toList(), if (fbItems.isEmpty()) fbErr else null, usedFallback = true)
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

        val sorted = when (filters.sort) {
            SortOrder.RELEVANCE -> deduped.values.toList()
            SortOrder.RESOLUTION -> deduped.values.sortedByDescending { it.width.toLong() * it.height.toLong() }
        }

        withContext(Dispatchers.Default) { sorted } to statuses
    }
}
