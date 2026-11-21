package com.sandeep.imghunt.engine

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
internal fun String.httpOrNull(): String? = takeIf { it.startsWith("https://") || it.startsWith("http://") }

internal object OpenCatalog {
    fun parse(provider: SourceProvider, root: JSONObject, page: Int): CatalogPage {
        val photos = mutableListOf<PhotoItem>()
        val more: Boolean
        when (provider) {
            SourceProvider.OPENVERSE -> {
                require(root.has("results")) { "Invalid Openverse response" }
                for (item in root.optJSONArray("results").objects()) {
                    val full = item.optString("url").httpOrNull() ?: continue
                    val license = item.optString("license").uppercase().ifBlank { "Check source license" }
                    photos += PhotoItem("ov_${item.optString("id")}", item.optString("title").ifBlank { "Openverse image" },
                        item.optString("thumbnail").httpOrNull() ?: full, full, item.optString("foreign_landing_url"), provider,
                        item.optInt("width"), item.optInt("height"), isOpenLicense = true,
                        licenseLabel = license, creator = item.optString("creator").takeIf { it.isNotBlank() })
                }
                more = !root.isNull("next") && root.optString("next").isNotBlank()
            }
            SourceProvider.WIKIMEDIA -> {
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
                for (key in pages.keys()) {
                    val item = pages.optJSONObject(key) ?: continue
                    val info = item.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                    val full = info.optString("url").httpOrNull() ?: continue
                    val mime = info.optString("mime")
                    if (!mime.startsWith("image/") || mime == "image/svg+xml") continue
                    val metadata = info.optJSONObject("extmetadata")
                    val license = metadata?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
                    val artist = metadata?.optJSONObject("Artist")?.optString("value").orEmpty().replace(Regex("<[^>]*>"), "")
                    photos += PhotoItem("wm_$key", item.optString("title").removePrefix("File:"),
                        info.optString("thumburl").httpOrNull() ?: full, full, info.optString("descriptionurl"), provider,
                        info.optInt("width"), info.optInt("height"), mime, true, license.ifBlank { "Check Commons license" }, artist)
                }
                more = root.has("continue")
            }
            SourceProvider.ARCHIVE -> {
                val response = root.optJSONObject("response") ?: error("Invalid Archive response")
                for (item in response.optJSONArray("docs").objects()) {
                    val id = item.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                    val preview = "https://archive.org/services/img/$id"
                    photos += PhotoItem("ia_$id", item.optString("title").ifBlank { id }, preview, preview,
                        "https://archive.org/details/$id", provider, licenseLabel = "Preview; check source rights",
                        creator = item.optString("creator").takeIf { it.isNotBlank() })
                }
                more = response.optLong("numFound") > page.toLong() * 36
            }
            SourceProvider.NASA -> {
                val collection = root.optJSONObject("collection") ?: error("Invalid NASA response")
                for (item in collection.optJSONArray("items").objects()) {
                    val data = item.optJSONArray("data")?.optJSONObject(0) ?: continue
                    val full = item.optJSONArray("links")?.optJSONObject(0)?.optString("href")?.httpOrNull() ?: continue
                    val id = data.optString("nasa_id")
                    photos += PhotoItem("nasa_$id", data.optString("title").ifBlank { id }, full, full,
                        "https://images.nasa.gov/details/$id", provider, licenseLabel = "Check NASA media usage guidelines",
                        creator = data.optString("center").takeIf { it.isNotBlank() })
                }
                more = collection.optJSONObject("metadata")?.optLong("total_hits", 0)?.let { it > page.toLong() * 36 } ?: false
            }
            else -> error("Not an open catalog")
        }
        return CatalogPage(photos, more)
    }
}
