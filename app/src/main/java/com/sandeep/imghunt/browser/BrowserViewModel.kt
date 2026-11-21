package com.sandeep.imghunt.browser

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sandeep.imghunt.engine.PhotoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONTokener

data class BrowserState(val query: String = "", val pageUrl: String = "https://www.google.com/imghp",
    val title: String = "Google Images", val progress: Int = 0, val canBack: Boolean = false, val canForward: Boolean = false,
    val error: String? = null, val message: String? = null, val images: List<PhotoItem> = emptyList(),
    val collecting: Boolean = false, val pending: BrowserCandidate? = null)
class BrowserViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(BrowserState(query = saved["browser.query"] ?: "",
        pageUrl = BrowserImagePolicy.https(saved["browser.url"] ?: "") ?: "https://www.google.com/imghp"))
    val state = mutable.asStateFlow()
    val initialUrl: String get() = state.value.pageUrl
    var history: Bundle? = null
    private var captureJob: Job? = null
    private var generation = 0
    fun query(value: String) { saved["browser.query"] = value.take(2000); mutable.update { it.copy(query = value.take(2000)) } }
    fun page(url: String?, title: String?, back: Boolean, forward: Boolean) {
        val safe = url?.let(BrowserImagePolicy::https)
        safe?.let { saved["browser.url"] = it }
        mutable.update { it.copy(pageUrl = safe ?: it.pageUrl, title = title?.take(160) ?: it.title, canBack = back, canForward = forward) }
    }
    fun loading(progress: Int) { mutable.update { it.copy(progress = progress.coerceIn(0,100)) } }
    fun error(value: String?) { mutable.update { it.copy(error = value) } }
    fun message(value: String?) { mutable.update { it.copy(message = value) } }
    fun select(candidate: BrowserCandidate) {
        if (BrowserImagePolicy.https(candidate.src) == null && !BrowserImagePolicy.embedded(candidate.src)) {
            message("This image format is unsupported. Open the image or source page and try again."); return
        }
        mutable.update { it.copy(pending = candidate) }
    }
    fun dismissSelection() { mutable.update { it.copy(pending = null) } }
    fun addSelected() { val selected = state.value.pending ?: return; dismissSelection(); capture { listOf(selected) } }
    fun collected(raw: String?, page: String) = capture {
        require(raw != null && raw.length <= 700000)
        val decoded = JSONTokener(raw).nextValue()
        val array = JSONArray(if (decoded is String) decoded else raw)
        (0 until minOf(array.length(),40)).mapNotNull { i -> array.optJSONObject(i)?.let {
            BrowserCandidate(it.optString("src"), page, it.optString("title"), it.optString("anchor"), it.optInt("width"), it.optInt("height"))
        } }
    }
    private fun capture(read: () -> List<BrowserCandidate>) {
        generation++; val ticket = generation; captureJob?.cancel(); mutable.update { it.copy(collecting = true) }
        captureJob = viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) { read() }; var added = 0; var skipped = 0
                for (candidate in candidates.take(40)) {
                    ensureActive(); if (state.value.images.size >= BrowserImagePolicy.MAX_TRAY) break
                    try {
                        val photo = withContext(Dispatchers.IO) { BrowserImageStore.capture(getApplication(), candidate) }
                        if (ticket == generation && state.value.images.none { it.highResUrl == photo.highResUrl }) {
                            mutable.update { it.copy(images = it.images + photo) }; added++
                        }
                    } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { skipped++ }
                }
                if (ticket == generation) message(if (added == 0) "No new supported images. Open an image and long-press it, or scroll to load more."
                    else "$added image(s) added" + if (skipped > 0) "; $skipped unsupported preview(s) skipped" else "")
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { if (ticket == generation) message("Could not collect this page. Try long-pressing an image.")
            } finally { if (ticket == generation) mutable.update { it.copy(collecting = false) } }
        }
    }
    fun clearTray() { generation++; captureJob?.cancel(); mutable.update { it.copy(images = emptyList(), pending = null, collecting = false) } }
    fun save(photo: PhotoItem) { viewModelScope.launch {
        try { BrowserImageDownloads.enqueue(getApplication(), photo); message("Image queued for saving") }
        catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { message("Could not queue image; select it again") }
    } }
}
