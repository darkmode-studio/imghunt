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

enum class AspectFilter(val label: String) {
    ANY("Any"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    SQUARE("Square")
}

enum class ResolutionFilter(val label: String, val minWidth: Int, val minHeight: Int) {
    ANY("Any", 0, 0),
    HD("HD (1280+)", 1280, 720),
    FHD("Full HD (1920+)", 1920, 1080),
    UHD("4K (3840+)", 3840, 2160)
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
    val license: LicenseFilter = LicenseFilter.ALL
) {
    fun matches(photo: PhotoItem): Boolean {
        if (resolution != ResolutionFilter.ANY && photo.width > 0 && photo.height > 0) {
            val maxSide = maxOf(photo.width, photo.height)
            if (maxSide < resolution.minWidth) return false
        }
        if (aspect != AspectFilter.ANY && photo.width > 0 && photo.height > 0) {
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
    val licenseLabel: String = "Open / Public"
) {
    fun inferredExtension(): String {
        val m = mimeType?.substringAfter("/")?.substringBefore(";")?.lowercase()
        if (!m.isNullOrBlank()) return if (m == "jpeg") "jpg" else m
        val clean = highResUrl.substringBefore("?").substringBefore("#").substringAfterLast(".", "")
        return clean.lowercase().ifBlank { "jpg" }
    }
}

data class ProviderResult(
    val provider: SourceProvider,
    val items: List<PhotoItem>,
    val error: String? = null,
    val usedFallback: Boolean = false
)
