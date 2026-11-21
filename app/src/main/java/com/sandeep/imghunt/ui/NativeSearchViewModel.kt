package com.sandeep.imghunt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.browser.BrowserImageDownloads
import com.sandeep.imghunt.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NativeSearchState(val query: String = "", val mode: SearchMode = SearchMode.CATALOGS,
    val images: List<PhotoItem> = emptyList(), val favorites: List<PhotoItem> = emptyList(),
    val history: List<String> = emptyList(), val filters: SearchFilters = SearchFilters(),
    val providers: Set<SourceProvider> = emptySet(), val states: List<ProviderResult> = emptyList(),
    val loading: Boolean = false, val loadingMore: Boolean = false, val hasMore: Boolean = false,
    val hasSearched: Boolean = false, val error: String? = null, val note: String = "", val notice: String? = null)

class NativeSearchViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val free = setOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA, SourceProvider.ARCHIVE, SourceProvider.NASA, SourceProvider.MET)
    private val mutable = MutableStateFlow(NativeSearchState(query = saved["query"] ?: "", favorites = prefs.loadFavorites(),
        history = prefs.loadHistory(), providers = prefs.loadEnabledProviders().intersect(free).ifEmpty { free }))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var session: ImageSearchRepository? = null
    var pendingDownload: PhotoItem? = null
    fun query(value: String) { saved["query"] = value.take(400); mutable.update { it.copy(query = value.take(400)) } }
    fun mode(value: SearchMode) { /* This build intentionally uses free catalogs only. */ }
    fun cancel() { generation++; job?.cancel(); mutable.update { it.copy(loading = false, loadingMore = false) } }
    fun filters(value: SearchFilters) { cancel(); session = null; mutable.update { it.copy(filters = value, images = emptyList(), hasMore = false, hasSearched = false, error = null, note = "Tap Search to apply filters") } }
    fun search() {
        val current = state.value
        val q = current.query.trim()
        if (q.isBlank() || q.split(Regex("\\s+")).size > 50) { mutable.update { it.copy(error = "Enter 1 to 50 words") }; return }
        cancel(); val ticket = generation
        mutable.update { it.copy(loading = true, hasSearched = true, images = emptyList(), hasMore = false, states = emptyList(), error = null, note = "", history = prefs.addHistory(q)) }
        job = viewModelScope.launch {
            try {
                val repo = ImageSearchRepository(q, SearchMode.CATALOGS, current.filters, GatewayConfig("", ""), current.providers.intersect(free), emptyMap())
                session = repo; apply(repo.next(), ticket)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { if (ticket == generation) mutable.update { it.copy(error = friendlySearchError(error)) }
            } finally { if (ticket == generation) mutable.update { it.copy(loading = false) } }
        }
    }
    fun more() {
        val repo = session ?: return
        if (state.value.loading || state.value.loadingMore || !state.value.hasMore) return
        val ticket = generation; mutable.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            try { apply(repo.next(), ticket)
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { if (ticket == generation) mutable.update { it.copy(error = friendlySearchError(error)) }
            } finally { if (ticket == generation) mutable.update { it.copy(loadingMore = false) } }
        }
    }
    private fun apply(result: SearchSnapshot, ticket: Int) {
        if (ticket == generation) mutable.update { it.copy(images = result.items, hasMore = result.hasMore, states = result.states, note = result.note, error = result.error) }
    }
    fun provider(provider: SourceProvider, enabled: Boolean) {
        if (provider !in free) return
        cancel(); session = null
        val next = if (enabled) state.value.providers + provider else state.value.providers - provider
        prefs.saveEnabledProviders(next)
        mutable.update { it.copy(providers = next, images = emptyList(), hasMore = false, hasSearched = false, note = "Sources changed; tap Search") }
    }
    fun favorite(photo: PhotoItem) {
        val old = state.value.favorites
        val next = if (old.any { it.highResUrl == photo.highResUrl }) old.filterNot { it.highResUrl == photo.highResUrl } else (listOf(photo) + old).take(100)
        prefs.saveFavorites(next); mutable.update { it.copy(favorites = next) }
    }
    fun download(photo: PhotoItem) { viewModelScope.launch {
        try {
            if (photo.provider == SourceProvider.BROWSER) BrowserImageDownloads.enqueue(getApplication(), photo)
            else DownloadPipeline.enqueueHighRes(getApplication(), photo)
            notice("Image queued for saving")
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) { notice("Could not queue image. Select it again and retry.") }
    } }
    fun notice(value: String?) { mutable.update { it.copy(notice = value) } }
}
