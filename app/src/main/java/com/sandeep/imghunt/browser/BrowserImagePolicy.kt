package com.sandeep.imghunt.browser

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

data class BrowserCandidate(val src: String, val page: String, val title: String = "Website image",
    val anchor: String = "", val width: Int = 0, val height: Int = 0,
    val options: List<BrowserImageOption> = emptyList())
object BrowserImagePolicy {
    const val MAX_TRAY = 80
    const val MAX_EMBEDDED_CHARS = 262144
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) {
            if (b[0] == 0 || b[0] >= 224 || b[0] == 127 || b[0] == 10) return false
            if (b[0] == 100 && b[1] in 64..127) return false
            if (b[0] == 198 && b[1] in 18..19) return false
            if (b[0] == 192 && b[1] == 0 && b[2] in setOf(0, 2)) return false
            if (b[0] == 198 && b[1] == 51 && b[2] == 100) return false
            if (b[0] == 203 && b[1] == 0 && b[2] == 113) return false
        } else if (b.size == 16 && ((b[0] and 254) == 252 || (b[0] == 32 && b[1] == 1 && b[2] == 13 && b[3] == 184))) return false
        return true
    }
    fun https(value: String): String? = runCatching {
        require(value.length <= 8192 && value.none { it.isISOControl() })
        val uri = URI(value.trim()); val host = requireNotNull(uri.host).lowercase().trim('[', ']')
        require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in -1..65535)
        require(host != "localhost" && !host.endsWith(".local") && !host.endsWith(".localhost"))
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) require(publicAddress(InetAddress.getByName(host))) else require(host.contains('.'))
        uri.toASCIIString().substringBefore('#')
    }.getOrNull()
    fun address(input: String): String {
        val value = input.trim(); require(value.isNotBlank()) { "Enter a search term or HTTPS address" }
        if (value.startsWith("https://", true)) return https(value) ?: error("Use a public HTTPS website")
        require(!value.contains("://")) { "Only HTTPS websites are supported" }
        if (!value.contains(' ') && value.matches(Regex("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(?:/.*)?")))
            return https("https://$value") ?: error("Use a public HTTPS website")
        return "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(value.take(400), "UTF-8")
    }
    fun embedded(src: String): Boolean = src.length <= MAX_EMBEDDED_CHARS &&
        listOf("data:image/png;base64,", "data:image/jpeg;base64,", "data:image/webp;base64,", "data:image/gif;base64,").any { src.startsWith(it, true) }
    private fun google(uri: URI) = uri.host.equals("google.com", true) || uri.host.lowercase().endsWith(".google.com")
    private fun parameter(raw: String?, key: String): String? = raw?.split('&')?.firstNotNullOfOrNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2 && pair[0] == key) runCatching { URLDecoder.decode(pair[1], "UTF-8") }.getOrNull() else null
    }
    fun alternatives(candidate: BrowserCandidate): List<BrowserImageOption> = buildList {
        https(candidate.anchor)?.let { value ->
            val uri = URI(value)
            if (google(uri)) parameter(uri.rawQuery, "imgurl")?.let { https(it)?.let { url -> add(BrowserImageOption(url, kind = "Original link")) } }
        }
        addAll(candidate.options.take(16).mapNotNull { option -> https(option.url)?.let { option.copy(url = it, width = option.width.coerceIn(0,100000), height = option.height.coerceIn(0,100000)) } }
            .sortedWith(compareByDescending<BrowserImageOption> { if (it.kind == "Original link") 10000000 else if (it.kind == "Original hint") 9000000 else it.width }))
        https(candidate.src)?.let { add(BrowserImageOption(it, candidate.width, candidate.height, "Displayed image")) }
    }.distinctBy { it.url }
    fun imageUrl(candidate: BrowserCandidate): String? = alternatives(candidate).firstOrNull()?.url
    fun sourceUrl(candidate: BrowserCandidate): String {
        https(candidate.anchor)?.let { anchor ->
            val uri = URI(anchor)
            if (google(uri)) parameter(uri.rawQuery, "imgrefurl")?.let { https(it)?.let { source -> return source } }
            else if (!uri.path.orEmpty().matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|avif)", RegexOption.IGNORE_CASE))) return anchor
        }
        return https(candidate.page) ?: "https://www.google.com/imghp"
    }
    fun id(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    val collectScript: String get() = BrowserImageDiscovery.script
}
