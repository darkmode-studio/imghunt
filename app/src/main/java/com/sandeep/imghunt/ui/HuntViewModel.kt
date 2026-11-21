package com.sandeep.imghunt.ui

import android.app.Application
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
import com.sandeep.imghunt.engine.ResolutionFilter
import com.sandeep.imghunt.engine.SearchFilters
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HuntViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = AppPrefs(app)

    var query by mutableStateOf("")
    var filters by mutableStateOf(SearchFilters(resolution = ResolutionFilter.ANY))
    var enabledProviders by mutableStateOf(prefs.loadEnabledProviders())
    var apiKeys by mutableStateOf(prefs.loadKeys())
    private var _favorites by mutableStateOf(prefs.loadFavorites())
    val favorites: List<PhotoItem> get() = _favorites
    val favourites: List<PhotoItem> get() = _favorites
    var searchHistory by mutableStateOf(prefs.loadHistory())
    val history: List<String> get() = searchHistory

    var results by mutableStateOf<List<PhotoItem>>(emptyList())
    var providerStates by mutableStateOf<List<ProviderResult>>(emptyList())
    var currentPage by mutableIntStateOf(1)
    var canLoadMore by mutableStateOf(false)
    val hasMore: Boolean get() = canLoadMore
    var isSearching by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isOffline by mutableStateOf(false)
    var statusBanner by mutableStateOf<String?>(null)
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
    var activePhotoIndex by mutableStateOf<Int?>(null)

    private var searchJob: Job? = null

    val suggestions = listOf(
        "Kerala monsoon",
        "Street photography",
        "Minimal architecture",
        "Mountain mist",
        "Night sky",
        "Vintage botanical"
    )

    fun hasNetwork(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun refreshConnectivity() {
        isOffline = !hasNetwork()
    }

    fun startSearch(customQuery: String? = null) {
        val q = (customQuery ?: query).trim()
        if (q.isEmpty()) {
            statusBanner = "Enter a keyword to search high-res images"
            return
        }
        if (!hasNetwork()) {
            isOffline = true
            statusBanner = "Offline: check your connection and try again"
            return
        }
        isOffline = false
        if (customQuery != null) query = q
        searchJob?.cancel()
        selectedIds = emptySet()
        activePhotoIndex = null
        currentPage = 1
        canLoadMore = false
        isSearching = true
        statusBanner = null
        searchHistory = prefs.addHistory(q)

        searchJob = viewModelScope.launch {
            try {
                val (found, states) = PhotoSources.searchPage(
                    query = q,
                    page = 1,
                    filters = filters,
                    enabledProviders = enabledProviders,
                    apiKeys = apiKeys,
                    allowFallback = true
                )
                results = found
                providerStates = states
                canLoadMore = found.isNotEmpty()
                val fallbackUsed = states.any { it.usedFallback && it.items.isNotEmpty() }
                statusBanner = when {
                    found.isEmpty() -> "No matching images returned"
                    fallbackUsed -> "${found.size} results (fallback active)"
                    else -> "${found.size} results"
                }
            } catch (t: Throwable) {
                statusBanner = "Search failed: ${t.message ?: "unexpected error"}"
            } finally {
                isSearching = false
            }
        }
    }

    fun loadMore() {
        val q = query.trim()
        if (q.isEmpty() || isSearching || isLoadingMore || !canLoadMore) return
        if (!hasNetwork()) {
            isOffline = true
            statusBanner = "Offline: cannot load more right now"
            return
        }
        isOffline = false
        isLoadingMore = true
        val nextPage = currentPage + 1
        searchJob = viewModelScope.launch {
            try {
                val (moreItems, moreStates) = PhotoSources.searchPage(
                    query = q,
                    page = nextPage,
                    filters = filters,
                    enabledProviders = enabledProviders,
                    apiKeys = apiKeys,
                    allowFallback = false
                )
                val existingUrls = results.map { it.highResUrl }.toSet()
                val appended = moreItems.filter { it.highResUrl !in existingUrls }
                if (appended.isNotEmpty()) {
                    results = results + appended
                    currentPage = nextPage
                    statusBanner = "${results.size} results loaded"
                } else {
                    canLoadMore = false
                    statusBanner = "No additional pages (${results.size} total)"
                }
                if (moreStates.isNotEmpty()) providerStates = moreStates
            } catch (t: Throwable) {
                statusBanner = "Load more failed: ${t.message ?: "error"}"
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun runSearch(customQuery: String? = null, loadMore: Boolean = false) {
        if (loadMore) loadMore() else startSearch(customQuery)
    }

    fun toggleSelect(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun toggleSelect(item: PhotoItem) = toggleSelect(item.id)

    fun toggleSelectAll() {
        selectedIds = if (selectedIds.size == results.size) emptySet() else results.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    fun clearQueryAndResults() {
        query = ""
        results = emptyList()
        statusBanner = null
    }

    fun toggleFavorite(item: PhotoItem) {
        val exists = _favorites.any { it.highResUrl == item.highResUrl }
        _favorites = if (exists) _favorites.filterNot { it.highResUrl == item.highResUrl } else listOf(item) + _favorites
        prefs.saveFavorites(_favorites)
    }

    fun toggleFavourite(item: PhotoItem) = toggleFavorite(item)

    fun isFavorite(item: PhotoItem): Boolean = _favorites.any { it.highResUrl == item.highResUrl }

    fun isFavourite(item: PhotoItem): Boolean = isFavorite(item)

    fun enqueueBatch(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            items.forEach { DownloadPipeline.enqueueHighRes(ctx, it) }
        }
        selectedIds = emptySet()
    }

    fun toggleProvider(provider: SourceProvider, enabled: Boolean) {
        val next = if (enabled) enabledProviders + provider else enabledProviders - provider
        enabledProviders = next
        prefs.saveEnabledProviders(next)
    }

    fun saveApiKey(provider: SourceProvider, key: String) {
        val next = apiKeys.toMutableMap().apply {
            if (key.isBlank()) remove(provider) else put(provider, key.trim())
        }
        apiKeys = next
        prefs.saveKey(provider, key)
    }
}
