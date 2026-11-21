package com.sandeep.imghunt.engine

import com.sandeep.imghunt.data.GatewayAddress
import com.sandeep.imghunt.data.GatewayConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

class SearchSetupRequired : IOException("Configure your HTTPS search gateway in Search settings, or choose Open catalogs")
data class WebImageBatch(val items: List<PhotoItem>, val correctedQuery: String?)

object WebImageClient {
    private fun https(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun parse(raw: String): WebImageBatch {
        val root = JSONObject(raw)
        val array = root.optJSONArray("items") ?: throw IOException("Invalid gateway response")
        if (array.length() > 200) throw IOException("Gateway exceeded the image result limit")
        val photos = LinkedHashMap<String, PhotoItem>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val original = item.optString("imageUrl")
            val source = item.optString("sourcePageUrl")
            if (!https(original) || !runCatching { URI(source).scheme in setOf("https", "http") && URI(source).host != null }.getOrDefault(false)) continue
            val thumb = item.optString("thumbnailUrl").takeIf(::https) ?: original
            val id = MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
                .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            photos.putIfAbsent(original, PhotoItem(
                id = "web_$id", title = item.optString("title").ifBlank { "Web image" },
                previewUrl = thumb, highResUrl = original, sourcePageUrl = source,
                provider = SourceProvider.BRAVE, width = item.optInt("width").coerceAtLeast(0),
                height = item.optInt("height").coerceAtLeast(0), isOpenLicense = false,
                licenseLabel = "Rights unknown; check the source", sourceName = URI(source).host
            ))
        }
        return WebImageBatch(photos.values.toList(), root.optString("correctedQuery").takeIf { it.isNotBlank() && it != "null" })
    }

    suspend fun search(config: GatewayConfig, query: String): WebImageBatch {
        if (!config.configured) throw SearchSetupRequired()
        val base = GatewayAddress.normalize(config.baseUrl).toHttpUrl()
        val url = base.newBuilder().addPathSegments("v1/images/search")
            .addQueryParameter("q", query).addQueryParameter("count", "200").build()
        return parse(SearchHttp.get(url.toString(), mapOf("Authorization" to "Bearer ${config.accessToken}")))
    }
}

/** Memory-only browsing of a single search response; scrolling never calls the provider again. */
class ResultWindow(items: List<PhotoItem>, private val pageSize: Int = 24) {
    private val all = items.distinctBy { it.highResUrl }
    private var visible = 0
    init { require(pageSize > 0) }
    val hasMore: Boolean get() = visible < all.size
    val total: Int get() = all.size
    fun revealNext(): List<PhotoItem> {
        visible = minOf(all.size, visible + pageSize)
        return all.take(visible)
    }
}
