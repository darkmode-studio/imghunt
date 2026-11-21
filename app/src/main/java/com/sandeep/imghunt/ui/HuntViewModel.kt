package com.sandeep.imghunt.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandeep.imghunt.data.AppPrefs
import com.sandeep.imghunt.data.DownloadPipeline
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.engine.ProviderResult
import com.sandeep.imghunt.engine.SearchFilters
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HuntViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val prefs = AppPrefs(context)

    var query by mutableStateOf("")
    var filters by mutableStateOf(SearchFilters())
    var enabledProviders by mutableStateOf(prefs.loadEnabledProviders())
    var apiKeys by mutableStateOf(prefs.loadKeys())
    var favourites by mutableStateOf<List<PhotoItem>>(emptyList())
    var history by mutableStateOf<List<String>>(emptyList())

    var results by mutableStateOf<List<PhotoItem>>(emptyList())
    var providerStates by mutableStateOf<List<ProviderResult>>(emptyList())
    var currentPage by mutableIntStateOf(0)
    var hasMore by mutableStateOf(false)
    var isSearching by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isOffline by mutableStateOf(!isNetworkAvailable(context))
    var statusBanner by mutableStateOf<String?>(null)

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
    var activePhotoIndex by mutableStateOf<Int?>(null)

    val suggestions = listOf("Kerala monsoon", "Architecture", "Wildlife", "Space nebula", "Minimal portrait", "Night street")

    private var searchJob: Job? = null

    fun refreshConnectivity() {
        isOffline = !isNetworkAvailable(context)
    }

    fun toggleSelect(item: PhotoItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
    }

    fun toggleSelectAll() {
        selectedIds = if (selectedIds.size == results.size) emptySet() else results.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    fun toggleFavourite(item: PhotoItem) {
        favourites = if (favourites.any { it.id == item.id }) {
            favourites.filterNot { it.id == item.id }
        } else {
            listOf(item) + favourites
        }
    }

    fun isFavourite(item: PhotoItem): Boolean = favourites.any { it.id == item.id }

    fun runSearch(customQuery: String? = null, loadMore: Boolean = false) {
        refreshConnectivity()
        val q = (customQuery ?: query).trim()
        if (q.isEmpty()) {
            statusBanner = "Enter a keyword to search"
            return
        }
        if (customQuery != null) query = q
        if (isOffline) {
            statusBanner = "No internet connection — showing saved items if available"
            return
        }

        val targetPage = if (loadMore) currentPage + 1 else 0
        searchJob?.cancel()

        if (!loadMore) {
            selectedIds = emptySet()
            activePhotoIndex = null
            results = emptyList()
            currentPage = 0
            hasMore = false
            isSearching = true
            statusBanner = null
            history = (listOf(q) + history.filterNot { it.equals(q, ignoreCase = true) }).take(12)
        } else {
            isLoadingMore = true
        }

        searchJob = viewModelScope.launch {
            try {
                val (found, states) = PhotoSources.searchWithFallback(
                    query = q,
                    filters = filters,
                    page = targetPage,
                    enabledProviders = enabledProviders,
                    apiKeys = apiKeys
                )
                val merged = if (loadMore) {
                    val map = LinkedHashMap<String, PhotoItem>()
                    results.forEach { map[it.highResUrl] = it }
                    found.forEach { map.putIfAbsent(it.highResUrl, it) }
                    map.values.toList()
                } else {
                    found
                }
                val addedCount = if (loadMore) (merged.size - results.size).coerceAtLeast(0) else merged.size
                results = merged
                providerStates = states
                currentPage = targetPage
                hasMore = found.isNotEmpty() && (!loadMore || addedCount > 0)

                val fallbackUsed = states.any { it.usedFallback && it.items.isNotEmpty() }
                statusBanner = when {
                    merged.isEmpty() -> "No matching images returned"
                    fallbackUsed -> "${merged.size} results (fallback active)"
                    else -> "${merged.size} results"
                }
            } catch (t: Throwable) {
                statusBanner = t.message ?: "Search failed"
            } finally {
                isSearching = false
                isLoadingMore = false
            }
        }
    }

    fun enqueueBatch(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { DownloadPipeline.enqueueHighRes(context, it) }
        }
        selectedIds = emptySet()
    }

    fun toggleProvider(provider: SourceProvider, enabled: Boolean) {
        val next = if (enabled) enabledProviders + provider else enabledProviders - provider
        enabledProviders = next
        prefs.saveEnabledProviders(next)
    }

    fun saveApiKey(provider: SourceProvider, key: String) {
        val nextKeys = apiKeys.toMutableMap().apply {
            if (key.isBlank()) remove(provider) else put(provider, key.trim())
        }
        apiKeys = nextKeys
        prefs.saveKey(provider, key)
    }

    companion object {
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
