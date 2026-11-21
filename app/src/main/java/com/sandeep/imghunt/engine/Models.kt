package com.sandeep.imghunt.engine

import java.net.URI

enum class SourceProvider(val label: String, val requiresKey: Boolean = false) {
    OPENVERSE("Openverse"), WIKIMEDIA("Wikimedia Commons"), ARCHIVE("Internet Archive"),
    NASA("NASA Images"), MET("Met Museum"), UNSPLASH("Unsplash", true),
    PEXELS("Pexels", true), PIXABAY("Pixabay", true), BRAVE("Web images"), BROWSER("Browser image")
}
enum class SortOrder(val label: String) { RELEVANCE("Relevance"), RESOLUTION("Highest Res"), SOURCE("Source") }
enum class AspectFilter(val label: String) { ANY("Any"), LANDSCAPE("Landscape"), PORTRAIT("Portrait"), SQUARE("Square") }
enum class ResolutionFilter(val label: String, val minLongSide: Int, val minShortSide: Int) {
    ANY("Any", 0, 0), HD("HD", 1280, 720), FHD("FHD", 1920, 1080), UHD("4K", 3840, 2160)
}
enum class FormatFilter(val label: String) { ANY("Any"), JPG("JPG"), PNG("PNG"), WEBP("WebP") }
enum class LicenseFilter(val label: String) { ALL("All"), OPEN("Open / CC") }
data class SearchFilters(val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val aspect: AspectFilter = AspectFilter.ANY, val format: FormatFilter = FormatFilter.ANY,
    val license: LicenseFilter = LicenseFilter.ALL, val sort: SortOrder = SortOrder.RELEVANCE) {
    val sortBy: SortOrder get() = sort
    fun matches(photo: PhotoItem): Boolean {
        val dims = photo.width > 0 && photo.height > 0
        if (resolution != ResolutionFilter.ANY) {
            if (!dims || maxOf(photo.width, photo.height) < resolution.minLongSide || minOf(photo.width, photo.height) < resolution.minShortSide) return false
        }
        if (aspect != AspectFilter.ANY) {
            if (!dims) return false
            when (aspect) {
                AspectFilter.LANDSCAPE -> if (photo.width <= photo.height) return false
                AspectFilter.PORTRAIT -> if (photo.height <= photo.width) return false
                AspectFilter.SQUARE -> if (photo.width.toFloat() / photo.height !in 0.85f..1.18f) return false
                AspectFilter.ANY -> Unit
            }
        }
        if (format != FormatFilter.ANY) {
            val expected = if (format == FormatFilter.JPG) setOf("JPG", "JPEG") else setOf(format.name)
            if (photo.inferredExtension().uppercase() !in expected) return false
        }
        return license != LicenseFilter.OPEN || photo.isOpenLicense
    }
}
data class PhotoItem(val id: String, val title: String, val previewUrl: String, val highResUrl: String,
    val sourcePageUrl: String, val provider: SourceProvider, val width: Int = 0, val height: Int = 0,
    val mimeType: String? = null, val isOpenLicense: Boolean = false,
    val licenseLabel: String = "Rights unknown; check source", val creator: String? = null, val sourceName: String? = null) {
    val sourceLabel: String get() = sourceName?.takeIf { it.isNotBlank() }
        ?: if (provider in setOf(SourceProvider.BRAVE, SourceProvider.BROWSER)) runCatching { URI(sourcePageUrl).host }.getOrNull() ?: provider.label else provider.label
    fun inferredExtension(): String {
        val mime = mimeType?.substringAfter('/')?.substringBefore(';')?.trim()?.lowercase()
        if (!mime.isNullOrBlank()) return when (mime) { "jpeg" -> "jpg"; "svg+xml" -> "svg"; else -> mime }
        return when (val ext = highResUrl.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "jpg"
            "png", "webp", "gif", "tif", "tiff", "avif" -> ext
            else -> "unknown"
        }
    }
    fun attributionText(): String = buildList {
        add(title); creator?.takeIf { it.isNotBlank() }?.let { add("by $it") }
        add(licenseLabel); add(sourceLabel)
        if (sourcePageUrl.startsWith("http")) add(sourcePageUrl)
    }.joinToString(" · ")
}
data class ProviderResult(val provider: SourceProvider, val items: List<PhotoItem>, val error: String? = null,
    val usedFallback: Boolean = false, val hasMore: Boolean? = null)
data class CatalogPage(val items: List<PhotoItem>, val hasMore: Boolean)
