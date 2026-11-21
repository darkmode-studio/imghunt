package com.sandeep.imghunt.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PhotoSources {

    private const val APP_UA = "ImgHunt/1.0 (Android; darkmode-studio/imghunt)"

    private fun getJson(urlStr: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 9000): String {
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

    private fun endpoint(provider: SourceProvider, q: String, rawQuery: String, key: String?): Pair<String, Map<String, String>> =
        when (provider) {
            SourceProvider.OPENVERSE ->
                "https://api.openverse.org/v1/images/?q=$q&page_size=24" to emptyMap()
            SourceProvider.WIKIMEDIA ->
                "https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrnamespace=6&gsrlimit=24&gsrsearch=$q&prop=imageinfo&iiprop=url|size|mime" to emptyMap()
            SourceProvider.ARCHIVE ->
                "https://archive.org/advancedsearch.php?q=${enc("mediatype:image AND ($rawQuery)")}&fl[]=identifier,title&rows=20&page=1&output=json" to emptyMap()
            SourceProvider.NASA ->
                "https://images-api.nasa.gov/search?q=$q&media_type=image&page_size=20" to emptyMap()
            SourceProvider.MET ->
                "https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&q=$q" to emptyMap()
            SourceProvider.UNSPLASH ->
                "https://api.unsplash.com/search/photos?query=$q&per_page=24" to mapOf("Authorization" to "Client-ID ${key.orEmpty()}")
            SourceProvider.PEXELS ->
                "https://api.pexels.com/v1/search?query=$q&per_page=24" to mapOf("Authorization" to key.orEmpty())
            SourceProvider.PIXABAY ->
                "https://pixabay.com/api/?key=${enc(key.orEmpty())}&q=$q&image_type=photo&per_page=24" to emptyMap()
        }

    fun fetchProvider(provider: SourceProvider, query: String, apiKey: String? = null): List<PhotoItem> {
        if (provider.requiresKey && apiKey.isNullOrBlank()) error("API key required for ${provider.label}")
        val (url, headers) = endpoint(provider, enc(query), query, apiKey)
        val raw = getJson(url, headers)
        return when (provider) {
            SourceProvider.OPENVERSE -> {
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
                val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
                buildList {
                    val keys = pages.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val p = pages.optJSONObject(k) ?: continue
                        val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                        val full = info.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val mime = info.optString("mime").ifBlank { null }
                        if (mime == "image/svg+xml") continue
                        add(PhotoItem("wm_$k", p.optString("title").removePrefix("File:").ifBlank { "Wikimedia Image" }, full, full, info.optString("descriptionurl"), provider, info.optInt("width", 0), info.optInt("height", 0), mime, true, "Wikimedia"))
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
                        add(PhotoItem("ia_$id", d.optString("title").ifBlank { id }, img, img, "https://archive.org/details/$id", provider, 0, 0, null, true, "Archive"))
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
                        val nid = data.optString("nasa_id").ifBlank { "$i" }
                        add(PhotoItem("nasa_$nid", data.optString("title").ifBlank { nid }, href, href, "https://images.nasa.gov/details/$nid", provider, 0, 0, null, true, "NASA"))
                    }
                }
            }
            SourceProvider.MET -> {
                val ids = JSONObject(raw).optJSONArray("objectIDs") ?: JSONArray()
                buildList {
                    for (i in 0 until minOf(ids.length(), 10)) {
                        val id = ids.optInt(i, -1)
                        if (id <= 0) continue
                        val objRaw = runCatching { getJson("https://collectionapi.metmuseum.org/public/collection/v1/objects/$id", timeoutMs = 4500) }.getOrNull() ?: continue
                        val obj = JSONObject(objRaw)
                        val full = obj.optString("primaryImage").takeIf { it.startsWith("http") } ?: continue
                        val thumb = obj.optString("primaryImageSmall").takeIf { it.startsWith("http") } ?: full
                        val open = obj.optBoolean("isPublicDomain", false)
                        add(PhotoItem("met_$id", obj.optString("title").ifBlank { "Met #$id" }, thumb, full, obj.optString("objectURL"), provider, 0, 0, null, open, if (open) "Met Open" else "Met"))
                    }
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
                        add(PhotoItem("us_${o.optString("id", "$i")}", o.optString("alt_description").ifBlank { "Unsplash Photo" }, thumb, full, o.optJSONObject("links")?.optString("html").orEmpty(), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Unsplash"))
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
                        add(PhotoItem("px_${o.optInt("id", i)}", o.optString("alt").ifBlank { "Pexels Photo" }, thumb, full, o.optString("url"), provider, o.optInt("width", 0), o.optInt("height", 0), null, true, "Pexels"))
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

        val statuses = primaryList.map { p ->
            async(Dispatchers.IO) {
                val r = withTimeoutOrNull(9500L) { runCatching { fetchProvider(p, query, apiKeys[p]) } }
                when {
                    r == null -> ProviderResult(p, emptyList(), "Timed out")
                    r.isFailure -> ProviderResult(p, emptyList(), r.exceptionOrNull()?.message ?: "Error")
                    else -> ProviderResult(p, r.getOrDefault(emptyList()))
                }
            }
        }.awaitAll().toMutableList()

        val deduped = LinkedHashMap<String, PhotoItem>()
        for (st in statuses) {
            for (item in st.items) {
                if (filters.matches(item)) deduped.putIfAbsent(item.highResUrl, item)
            }
        }

        if (deduped.isEmpty()) {
            val fallbacks = SourceProvider.entries.filter { it !in primaryList && (!it.requiresKey || !apiKeys[it].isNullOrBlank()) }
            if (fallbacks.isNotEmpty()) {
                val fbList = fallbacks.map { fb ->
                    async(Dispatchers.IO) {
                        val r = withTimeoutOrNull(9500L) { runCatching { fetchProvider(fb, query, apiKeys[fb]) } }
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
