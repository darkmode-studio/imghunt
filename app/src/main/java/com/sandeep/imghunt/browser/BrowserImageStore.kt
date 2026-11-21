package com.sandeep.imghunt.browser

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.SourceProvider
import java.io.File
import java.net.URI

object BrowserImageStore {
    fun directory(context: Context) = File(context.cacheDir, "browser_previews").apply { mkdirs() }
    fun bounds(file: File): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= 80000000L &&
            options.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
            "This is not a supported image, or its dimensions are too large"
        }
        return options
    }
    fun cachedFile(context: Context, value: String): File {
        val uri = URI(value)
        require(uri.scheme == "file" && uri.authority.isNullOrBlank()) { "Unsupported local preview" }
        val file = File(uri).canonicalFile
        require(file.parentFile == directory(context).canonicalFile && file.isFile) {
            "This temporary preview expired. Select the image again."
        }
        return file
    }
    fun capture(context: Context, candidate: BrowserCandidate): PhotoItem {
        val publicPreview = BrowserImagePolicy.https(candidate.src)
        var local: File? = null
        var width = candidate.width.coerceIn(0, 100000)
        var height = candidate.height.coerceIn(0, 100000)
        if (publicPreview == null) {
            require(BrowserImagePolicy.embedded(candidate.src)) { "Select a public HTTPS image or a supported embedded preview" }
            val bytes = Base64.decode(candidate.src.substringAfter(','), Base64.DEFAULT)
            require(bytes.isNotEmpty() && bytes.size <= 256 * 1024) { "Embedded preview is too large" }
            val folder = directory(context)
            val existing = folder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }
            var size = existing.sumOf { it.length() }
            for (old in existing) {
                if (size <= 40L * 1024 * 1024) break
                size -= old.length(); old.delete()
            }
            local = File(folder, BrowserImagePolicy.id(candidate.src) + ".preview")
            local.writeBytes(bytes)
            val info = try { bounds(local) } catch (error: Exception) { local.delete(); throw error }
            width = info.outWidth; height = info.outHeight
        }
        val preview = publicPreview ?: checkNotNull(local).toURI().toString()
        val original = BrowserImagePolicy.imageUrl(candidate) ?: preview
        val isPreviewOnly = original == preview
        val source = BrowserImagePolicy.sourceUrl(candidate)
        return PhotoItem(id = "browser_" + BrowserImagePolicy.id(original),
            title = candidate.title.filterNot { it.isISOControl() }.take(160).ifBlank { "Website image" },
            previewUrl = preview, highResUrl = original, sourcePageUrl = source, provider = SourceProvider.BROWSER,
            width = if (isPreviewOnly) width else 0, height = if (isPreviewOnly) height else 0,
            isOpenLicense = false, licenseLabel = if (isPreviewOnly) "Selected image/preview; check source rights" else "Original link; rights unverified",
            sourceName = URI(source).host)
    }
}
