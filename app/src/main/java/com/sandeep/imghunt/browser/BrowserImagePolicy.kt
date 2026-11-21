package com.sandeep.imghunt.browser

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/** Data from a selected image or an explicit collection of the currently loaded document. */
data class BrowserCandidate(val src: String, val page: String, val title: String = "Website image",
    val anchor: String = "", val width: Int = 0, val height: Int = 0)

object BrowserImagePolicy {
    const val MAX_TRAY = 80
    const val MAX_EMBEDDED_CHARS = 262144
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) {
            if (b[0] == 0 || b[0] >= 224 || b[0] == 127 || b[0] == 10) return false
            if (b[0] == 100 && b[1] in 64..127) return false
            if (b[0] == 198 && b[1] in 18..19) return false
            if (b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) return false
            if (b[0] == 198 && b[1] == 51 && b[2] == 100) return false
            if (b[0] == 203 && b[1] == 0 && b[2] == 113) return false
        } else if (b.size == 16 && ((b[0] and 254) == 252 ||
            (b[0] == 32 && b[1] == 1 && b[2] == 13 && b[3] == 184))) return false
        return true
    }
    fun https(value: String): String? = runCatching {
        require(value.length <= 8192 && value.none { it.isISOControl() })
        val uri = URI(value.trim())
        val host = uri.host?.lowercase()?.trim('[', ']') ?: return null
        require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in -1..65535)
        require(host != "localhost" && !host.endsWith(".local") && !host.endsWith(".localhost"))
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) require(publicAddress(InetAddress.getByName(host)))
        else require(host.contains('.'))
        URI(uri.scheme.lowercase(), uri.rawAuthority, uri.rawPath, uri.rawQuery, null).toASCIIString()
    }.getOrNull()
    fun address(input: String): String {
        val value = input.trim()
        require(value.isNotBlank()) { "Enter a search term or HTTPS address" }
        if (value.startsWith("https://", true)) return https(value)
            ?: throw IllegalArgumentException("Use a public HTTPS website")
        require(!value.contains("://")) { "Only HTTPS websites are supported" }
        return "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(value.take(400), "UTF-8")
    }
    fun embedded(src: String): Boolean = src.length <= MAX_EMBEDDED_CHARS &&
        listOf("data:image/png;base64,", "data:image/jpeg;base64,", "data:image/webp;base64,", "data:image/gif;base64,")
            .any { src.startsWith(it, true) }
    fun imageUrl(candidate: BrowserCandidate): String? {
        val anchor = https(candidate.anchor) ?: return https(candidate.src)
        val uri = URI(anchor)
        if (uri.host == "google.com" || uri.host.endsWith(".google.com")) {
            query(uri.rawQuery, "imgurl")?.let { https(it)?.let { full -> return full } }
        }
        return https(candidate.src)
    }
    fun sourceUrl(candidate: BrowserCandidate): String {
        val anchor = https(candidate.anchor)
        if (anchor != null) {
            val uri = URI(anchor)
            if (uri.host == "google.com" || uri.host.endsWith(".google.com")) {
                query(uri.rawQuery, "imgrefurl")?.let { https(it)?.let { source -> return source } }
            }
        }
        return https(candidate.page) ?: "https://www.google.com/imghp"
    }
    private fun query(raw: String?, key: String): String? = raw?.split('&')?.firstNotNullOfOrNull { pair ->
        val parts = pair.split('=', limit = 2)
        if (parts.size == 2 && parts[0] == key) runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull() else null
    }
    fun id(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .take(12).joinToString("") { "%02x".format(it.toInt() and 255) }

    // Constant script: no native bridge, credentials, hidden frames, network requests or automatic scrolling.
    val collectScript = """(function(){
      var out=[], seen={}, budget=0, imgs=document.images;
      for(var i=0;i<imgs.length && out.length<40;i++){
        var im=imgs[i], src=im.currentSrc||im.src||'';
        if(!im.complete || im.naturalWidth<80 || im.naturalHeight<80 || seen[src]) continue;
        if(src.indexOf('https://')!==0 && src.indexOf('data:image/')!==0) continue;
        if(src.length>262144) continue;
        var a=im.closest ? im.closest('a') : null;
        var item={src:src,anchor:a ? a.href : '',title:(im.alt||document.title||'Website image').slice(0,160),width:im.naturalWidth,height:im.naturalHeight};
        var text=JSON.stringify(item);
        if(budget+text.length>350000) break;
        budget+=text.length; seen[src]=true; out.push(item);
      }
      return JSON.stringify(out);
    })()""".trimIndent()
}
