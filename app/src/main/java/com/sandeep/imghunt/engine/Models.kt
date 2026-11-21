package com.sandeep.imghunt.engine

enum class SourceProvider(val label: String, val requiresKey: Boolean = false) {
    OPENVERSE("Openverse"),
    WIKIMEDIA("Wikimedia Commons"),
    ARCHIVE("Internet Archive"),
    NASA("NASA Images"),
    MET("Met Museum"),
    UNSPLASH("Unsplash", requiresKey = true),
    PEXELS("Pexels", requiresKey = true),
    PIXABAY("Pixabay", requiresKey = true)
}

enum class SortOrder(val label: String) {
    RELEVANCE("Relevance"),
    RESOLUTION("Highest Res")
}

enum class AspectFilter(val label: String) {
    ANY("Any"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    SQUARE("Square")
}

enum class ResolutionFilter(val label: String, val minLongSide: Int, val minShortSide: Int) {
    ANY("Any", 0, 0),
    HD("HD", 1280, 720),
    FHD("FHD", 1920, 1080),
    UHD("4K", 3840, 2160)
}

enum class FormatFilter(val label: String) {
    ANY("Any"),
    JPG("JPG"),
    PNG("PNG"),
    WEBP("WebP")
}

enum class LicenseFilter(val label: String) {
    ALL("All"),
    OPEN("Open / CC")
}

data class SearchFilters(
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val aspect: AspectFilter = AspectFilter.ANY,
    val format: FormatFilter = FormatFilter.ANY,
    val license: LicenseFilter = LicenseFilter.ALL,
    val sort: SortOrder = SortOrder.RELEVANCE
) {
    fun matches(photo: PhotoItem): Boolean {
        val hasDims = photo.width > 0 && photo.height > 0
        if (resolution != ResolutionFilter.ANY) {
            if (!hasDims) return false
            val longSide = maxOf(photo.width, photo.height)
            val shortSide = minOf(photo.width, photo.height)
            if (longSide < resolution.minLongSide || shortSide < resolution.minShortSide) return false
        }
        if (aspect != AspectFilter.ANY) {
            if (!hasDims) return false
            when (aspect) {
                AspectFilter.LANDSCAPE -> if (photo.width <= photo.height) return false
                AspectFilter.PORTRAIT -> if (photo.height <= photo.width) return false
                AspectFilter.SQUARE -> {
                    val ratio = photo.width.toFloat() / photo.height.toFloat()
                    if (ratio !in 0.85f..1.18f) return false
                }
                AspectFilter.ANY -> Unit
            }
        }
        if (format != FormatFilter.ANY) {
            val ext = photo.inferredExtension().uppercase()
            if (ext == "UNKNOWN") return false
            val expected = if (format == FormatFilter.JPG) setOf("JPG", "JPEG") else setOf(format.name)
            if (ext !in expected) return false
        }
        if (license == LicenseFilter.OPEN && !photo.isOpenLicense) return false
        return true
    }
}

data class PhotoItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val highResUrl: String,
    val sourcePageUrl: String,
    val provider: SourceProvider,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String? = null,
    val isOpenLicense: Boolean = true,
    val licenseLabel: String = "Open / Public",
    val creator: String? = null
) {
    fun inferredExtension(): String {
        val m = mimeType?.substringAfter("/")?.substringBefore(";")?.lowercase()?.trim()
        if (!m.isNullOrBlank()) {
            return when (m) {
                "jpeg" -> "jpg"
                "svg+xml" -> "svg"
                else -> m
            }
        }
        val pathOnly = highResUrl.substringBefore("?").substringBefore("#")
        val ext = pathOnly.substringAfterLast(".", "").lowercase()
        return when {
            ext in setOf("jpg", "jpeg") -> "jpg"
            ext in setOf("png", "webp", "gif", "tif", "tiff", "avif") -> ext
            else -> "unknown"
        }
    }

    fun attributionText(): String {
        val parts = buildList {
            add(title)
            if (!creator.isNullOrBlank()) add("by $creator")
            add(licenseLabel)
            add(provider.label)
            if (sourcePageUrl.startsWith("http")) add(sourcePageUrl)
        }
        return parts.joinToString(" · ")
    }
}

data class ProviderResult(
    val provider: SourceProvider,
    val items: List<PhotoItem>,
    val error: String? = null,
    val usedFallback: Boolean = false
)
