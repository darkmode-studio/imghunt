package com.sandeep.imghunt.engine

import org.json.JSONObject

internal object StockCatalog {
    fun parse(provider: SourceProvider, root: JSONObject, page: Int): CatalogPage {
        val key = when (provider) { SourceProvider.UNSPLASH -> "results"; SourceProvider.PEXELS -> "photos"; else -> "hits" }
        require(root.has(key)) { "Invalid ${provider.label} response" }
        val photos = root.optJSONArray(key).objects().mapNotNull { item ->
            val urls = item.optJSONObject(if (provider == SourceProvider.UNSPLASH) "urls" else "src")
            val full = when (provider) {
                SourceProvider.UNSPLASH -> urls?.optString("full")
                SourceProvider.PEXELS -> urls?.optString("original")
                else -> item.optString("largeImageURL")
            }?.httpOrNull() ?: return@mapNotNull null
            val thumb = when (provider) {
                SourceProvider.UNSPLASH -> urls?.optString("small")
                SourceProvider.PEXELS -> urls?.optString("medium")
                else -> item.optString("previewURL")
            }?.httpOrNull() ?: full
            val source = when (provider) {
                SourceProvider.UNSPLASH -> item.optJSONObject("links")?.optString("html").orEmpty()
                SourceProvider.PEXELS -> item.optString("url")
                else -> item.optString("pageURL")
            }
            val title = when (provider) {
                SourceProvider.UNSPLASH -> item.optString("alt_description")
                SourceProvider.PEXELS -> item.optString("alt")
                else -> item.optString("tags")
            }.ifBlank { "${provider.label} image" }
            val creator = when (provider) {
                SourceProvider.UNSPLASH -> item.optJSONObject("user")?.optString("name")
                SourceProvider.PEXELS -> item.optString("photographer")
                else -> item.optString("user")
            }
            PhotoItem("${provider.name}_${item.optString("id")}", title, thumb, full, source, provider,
                item.optInt(if (provider == SourceProvider.PIXABAY) "imageWidth" else "width"),
                item.optInt(if (provider == SourceProvider.PIXABAY) "imageHeight" else "height"),
                isOpenLicense = false, licenseLabel = "${provider.label} license; check source terms", creator = creator)
        }
        val more = when (provider) {
            SourceProvider.UNSPLASH -> root.optInt("total_pages") > page
            SourceProvider.PEXELS -> !root.isNull("next_page") && root.optString("next_page").isNotBlank()
            else -> root.optLong("totalHits") > page.toLong() * 30
        }
        return CatalogPage(photos, more)
    }
}
