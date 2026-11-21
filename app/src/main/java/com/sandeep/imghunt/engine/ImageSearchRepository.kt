package com.sandeep.imghunt.engine

import com.sandeep.imghunt.data.GatewayConfig
import kotlinx.coroutines.*
import java.io.IOException
import java.net.SocketTimeoutException

enum class SearchMode(val label: String) { WEB("Web images"), CATALOGS("Open catalogs") }
data class SearchSnapshot(val items: List<PhotoItem>, val hasMore: Boolean,
    val states: List<ProviderResult> = emptyList(), val note: String = "", val error: String? = null)

/** One query's session. Never shared between replacement searches. */
class ImageSearchRepository(
    private val query: String, private val mode: SearchMode, private val filters: SearchFilters,
    private val config: GatewayConfig, providers: Set<SourceProvider>,
    private val keys: Map<SourceProvider, String>
) {
    private val sources = providers.filter { it != SourceProvider.BRAVE }.sortedBy { it.ordinal }
    private val pages = sources.associateWith { 1 }.toMutableMap()
    private val exhausted = mutableSetOf<SourceProvider>()
    private val collected = LinkedHashMap<String, PhotoItem>()
    private var window: ResultWindow? = null
    private var webNote = ""

    suspend fun next(): SearchSnapshot {
        if (mode == SearchMode.WEB) {
            if (window == null) {
                val batch = WebImageClient.search(config, query)
                currentCoroutineContext().ensureActive()
                val filtered = PhotoSources.rankAndSort(batch.items.filter(filters::matches), filters.sort)
                window = ResultWindow(filtered)
                webNote = buildString {
                    append("${batch.items.size} web images returned; ${filtered.size} match your filters. ")
                    append("Up to 200 per query; rights must be checked at source.")
                    batch.correctedQuery?.let { append(" Search corrected to: $it") }
                }
            }
            val active = checkNotNull(window)
            return SearchSnapshot(active.revealNext(), active.hasMore, note = webNote)
        }
        if (sources.isEmpty()) return SearchSnapshot(emptyList(), false,
            error = "No catalogs selected. Open Search settings and enable a source.")
        val states = coroutineScope {
            sources.filterNot { it in exhausted }.map { provider -> async {
                if (provider.requiresKey && keys[provider].isNullOrBlank()) {
                    ProviderResult(provider, emptyList(), "API key required; configure this source", hasMore = false)
                } else try {
                    val result = withTimeoutOrNull(22000) {
                        PhotoSources.fetchPage(provider, query, pages.getValue(provider), keys[provider])
                    } ?: throw SocketTimeoutException()
                    ProviderResult(provider, result.items, hasMore = result.hasMore)
                } catch (cancel: CancellationException) { throw cancel
                } catch (error: Exception) {
                    ProviderResult(provider, emptyList(), friendlySearchError(error), hasMore = true)
                }
            } }.awaitAll()
        }
        currentCoroutineContext().ensureActive()
        for (state in states) {
            if (state.error == null) pages[state.provider] = pages.getValue(state.provider) + 1
            if (state.hasMore == false || pages.getValue(state.provider) > 100) exhausted += state.provider
        }
        // Interleave sources so one catalog does not fill every first-screen tile.
        val longest = states.maxOfOrNull { it.items.size } ?: 0
        for (index in 0 until longest) for (state in states) {
            state.items.getOrNull(index)?.takeIf(filters::matches)?.let { collected.putIfAbsent(it.highResUrl, it) }
        }
        val errors = states.count { it.error != null }
        val note = "${collected.size} catalog images" + if (errors > 0) " · $errors source(s) need attention in Settings" else ""
        return SearchSnapshot(PhotoSources.rankAndSort(collected.values.toList(), filters.sort),
            sources.any { it !in exhausted }, states, note,
            if (collected.isEmpty() && errors == states.size && errors > 0) "Sources could not return results. Check their status in Settings and retry." else null)
    }
}

fun friendlySearchError(error: Exception): String = when (error) {
    is SearchSetupRequired -> error.message.orEmpty()
    is SearchHttpException -> when (error.status) {
        401, 403 -> "Access denied. Check the access token or source key in Search settings."
        429 -> "Search quota or rate limit reached. Wait a minute before retrying."
        503 -> "Search service is not configured or is busy. Check the gateway and retry."
        504 -> "Search timed out. Please retry."
        else -> "Search service returned HTTP ${error.status}. Please retry."
    }
    is SocketTimeoutException -> "Search timed out. Please retry."
    is IOException -> "Network or search-response error. Check your connection and retry."
    else -> "This source could not complete the search. Try again or check Settings."
}
