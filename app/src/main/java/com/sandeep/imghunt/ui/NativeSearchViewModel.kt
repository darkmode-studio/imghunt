package com.sandeep.imghunt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NativeSearchState(
    val query: String = "", val mode: SearchMode = SearchMode.WEB,
    val images: List<PhotoItem> = emptyList(), val favorites: List<PhotoItem> = emptyList(),
    val history: List<String> = emptyList(), val filters: SearchFilters = SearchFilters(),
    val providers: Set<SourceProvider> = emptySet(), val states: List<ProviderResult> = emptyList(),
    val loading: Boolean = false, val loadingMore: Boolean = false, val hasMore: Boolean = false,
    val hasSearched: Boolean = false, val error: String? = null, val note: String = "",
    val gatewayUrl: String = "", val gatewayConfigured: Boolean = false,
    val setupRequired: Boolean = true, val settingsError: String? = null,
    val savingSettings: Boolean = false, val settingsRevision: Int = 0, val notice: String? = null
)

class NativeSearchViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val gateway = GatewaySettings(app)
    private val initialMode = runCatching { SearchMode.valueOf(saved.get<String>("mode") ?: "WEB") }.getOrDefault(SearchMode.WEB)
    private val mutable = MutableStateFlow(NativeSearchState(query = saved["query"] ?: "", mode = initialMode,
        favorites = prefs.loadFavorites(), history = prefs.loadHistory(), providers = prefs.loadEnabledProviders(),
        setupRequired = initialMode == SearchMode.WEB))
    val state: StateFlow<NativeSearchState> = mutable.asStateFlow()
    private var request: Job? = null
    private var generation = 0
    private var session: ImageSearchRepository? = null
    var pendingDownload: PhotoItem? = null

    init { viewModelScope.launch {
        val config = withContext(Dispatchers.IO) { gateway.load() }
        mutable.update { it.copy(gatewayUrl = config.baseUrl, gatewayConfigured = config.configured,
            setupRequired = it.mode == SearchMode.WEB && !config.configured) }
    } }
    fun query(value: String) { saved["query"] = value.take(400); mutable.update { it.copy(query = value.take(400)) } }
    fun mode(value: SearchMode) {
        cancel()
        saved["mode"] = value.name
        session = null
        mutable.update { it.copy(mode = value, images = emptyList(), hasMore = false, hasSearched = false,
            error = null, note = "", states = emptyList(), setupRequired = value == SearchMode.WEB && !it.gatewayConfigured) }
    }
    fun filters(value: SearchFilters) {
        cancel(); session = null
        mutable.update { it.copy(filters = value, images = emptyList(), hasMore = false, hasSearched = false,
            note = "Filters updated. Tap Search to apply.", error = null) }
    }
    fun cancel() {
        generation++; request?.cancel()
        mutable.update { it.copy(loading = false, loadingMore = false) }
    }
    fun search() {
        val current = state.value
        val query = current.query.trim()
        if (query.isBlank() || query.split(Regex("\\s+")).size > 50) {
            mutable.update { it.copy(error = "Enter 1 to 50 words to search") }; return
        }
        cancel()
        val ticket = generation
        mutable.update { it.copy(loading = true, error = null, images = emptyList(), hasMore = false,
            hasSearched = true, note = "", states = emptyList(), history = prefs.addHistory(query)) }
        request = viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { gateway.load() }
                val next = ImageSearchRepository(query, current.mode, current.filters, config, current.providers, prefs.loadKeys())
                session = next
                apply(next.next(), ticket)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { fail(error, ticket)
            } finally { if (ticket == generation) mutable.update { it.copy(loading = false) } }
        }
    }
    fun more() {
        val active = session ?: return
        if (state.value.loading || state.value.loadingMore || !state.value.hasMore) return
        val ticket = generation
        mutable.update { it.copy(loadingMore = true, error = null) }
        request = viewModelScope.launch {
            try { apply(active.next(), ticket)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { fail(error, ticket)
            } finally { if (ticket == generation) mutable.update { it.copy(loadingMore = false) } }
        }
    }
    private fun apply(snapshot: SearchSnapshot, ticket: Int) {
        if (ticket == generation) mutable.update { it.copy(images = snapshot.items, hasMore = snapshot.hasMore,
            states = snapshot.states, note = snapshot.note, error = snapshot.error, setupRequired = false) }
    }
    private fun fail(error: Exception, ticket: Int) {
        if (ticket == generation) mutable.update { it.copy(error = friendlySearchError(error),
            setupRequired = error is SearchSetupRequired || (error is SearchHttpException && error.status in listOf(401, 403))) }
    }
    fun saveGateway(url: String, token: String) { viewModelScope.launch {
        mutable.update { it.copy(savingSettings = true, settingsError = null) }
        try {
            val config = withContext(Dispatchers.IO) { gateway.save(url, token); gateway.load() }
            cancel(); session = null
            mutable.update { it.copy(gatewayUrl = config.baseUrl, gatewayConfigured = config.configured,
                setupRequired = false, images = emptyList(), hasMore = false, hasSearched = false,
                error = null, note = "Connection saved. Tap Search to test.", settingsRevision = it.settingsRevision + 1) }
        } catch (error: Exception) { mutable.update { it.copy(settingsError = error.message ?: "Could not save connection") }
        } finally { mutable.update { it.copy(savingSettings = false) } }
    } }
    fun provider(provider: SourceProvider, enabled: Boolean) {
        val next = if (enabled) state.value.providers + provider else state.value.providers - provider
        prefs.saveEnabledProviders(next)
        mutable.update { it.copy(providers = next) }
    }
    fun key(provider: SourceProvider, value: String) { prefs.saveKey(provider, value) }
    fun favorite(photo: PhotoItem) {
        val old = state.value.favorites
        val next = if (old.any { it.highResUrl == photo.highResUrl }) old.filterNot { it.highResUrl == photo.highResUrl } else (listOf(photo) + old).take(100)
        prefs.saveFavorites(next); mutable.update { it.copy(favorites = next) }
    }
    fun download(photo: PhotoItem) { viewModelScope.launch {
        try { DownloadPipeline.enqueueHighRes(getApplication(), photo); notice("Download queued")
        } catch (error: Exception) { notice("Could not queue download") }
    } }
    fun notice(value: String?) { mutable.update { it.copy(notice = value) } }
}
