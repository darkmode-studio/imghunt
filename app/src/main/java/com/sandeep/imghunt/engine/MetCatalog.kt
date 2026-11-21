package com.sandeep.imghunt.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

internal object MetCatalog {
    suspend fun parse(root: JSONObject, page: Int): CatalogPage = coroutineScope {
        val ids = root.optJSONArray("objectIDs")
        val start = (page - 1) * 6
        val end = minOf(ids?.length() ?: 0, start + 6)
        val items = (start until end).map { index -> async {
            val id = ids!!.optInt(index)
            val item = JSONObject(SearchHttp.get("https://collectionapi.metmuseum.org/public/collection/v1/objects/$id"))
            val full = item.optString("primaryImage").httpOrNull() ?: return@async null
            val open = item.optBoolean("isPublicDomain")
            PhotoItem("met_$id", item.optString("title").ifBlank { "Met $id" },
                item.optString("primaryImageSmall").httpOrNull() ?: full, full, item.optString("objectURL"), SourceProvider.MET,
                isOpenLicense = open, licenseLabel = if (open) "Met Open Access" else "Check Met rights",
                creator = item.optString("artistDisplayName"))
        } }.awaitAll().filterNotNull()
        CatalogPage(items, end < (ids?.length() ?: 0))
    }
}
