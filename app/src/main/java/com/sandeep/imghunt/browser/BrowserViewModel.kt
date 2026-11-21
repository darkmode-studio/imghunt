package com.sandeep.imghunt.browser

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.*
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.PhotoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*

data class BrowserState(val query: String = "", val pageUrl: String = "https://www.google.com/imghp",
    val title: String = "Google Images", val progress: Int = 0, val canBack: Boolean = false, val canForward: Boolean = false,
    val error: String? = null, val message: String? = null, val items: List<BrowserDetectedImage> = emptyList(),
    val collecting: Boolean = false, val pending: BrowserCandidate? = null, val showImages: Boolean = false,
    val selected: Set<String> = emptySet(), val batch: Set<String> = emptySet(),
    val transfers: Map<String, DownloadRecord> = emptyMap(), val queueErrors: Map<String,String> = emptyMap()) {
    val images: List<PhotoItem> get() = items.map { it.photo }
}
class BrowserViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(BrowserState(query=saved["browser.query"] ?: "",
        pageUrl=BrowserImagePolicy.https(saved["browser.url"] ?: "") ?: "https://www.google.com/imghp"))
    val state = mutable.asStateFlow()
    val initialUrl: String get() = state.value.pageUrl
    var history: Bundle? = null
    private var captureJob: Job? = null
    private var generation = 0
    init { viewModelScope.launch {
        ImgHuntDb.get(app).dao().observeAll().collect { records -> mutable.update {
            it.copy(transfers=records.asReversed().filter { record -> record.photoId.startsWith("browser_") }.associateBy { record -> record.photoId })
        } }
    } }
    fun query(value: String) { saved["browser.query"]=value.take(2000); mutable.update { it.copy(query=value.take(2000)) } }
    fun page(url: String?, title: String?, back: Boolean, forward: Boolean) {
        val safe=url?.let(BrowserImagePolicy::https); safe?.let { saved["browser.url"]=it }
        mutable.update { it.copy(pageUrl=safe ?: it.pageUrl,title=title?.take(160) ?: it.title,canBack=back,canForward=forward) }
    }
    fun loading(value: Int) { mutable.update { it.copy(progress=value.coerceIn(0,100)) } }
    fun error(value: String?) { mutable.update { it.copy(error=value) } }
    fun message(value: String?) { mutable.update { it.copy(message=value) } }
    fun results(open: Boolean) { mutable.update { it.copy(showImages=open) } }
    fun toggle(id: String) { mutable.update { it.copy(selected=if(id in it.selected) it.selected-id else it.selected+id) } }
    fun selectAll() { mutable.update { it.copy(selected=if(it.selected.size==it.items.size) emptySet() else it.items.map { row -> row.photo.id }.toSet()) } }
    fun choose(id: String, index: Int) { mutable.update { state -> state.copy(items=state.items.map { row ->
        if(row.photo.id!=id) row else row.options.getOrNull(index)?.let { option ->
            row.copy(selectedOption=index,photo=row.photo.copy(highResUrl=option.url,width=0,height=0,mimeType=null))
        } ?: row
    }) } }
    fun select(candidate: BrowserCandidate) {
        if(BrowserImagePolicy.https(candidate.src)==null && !BrowserImagePolicy.embedded(candidate.src)) {
            message("Unsupported image. Open its source page and scan again."); return
        }
        mutable.update { it.copy(pending=candidate) }
    }
    fun dismissSelection() { mutable.update { it.copy(pending=null) } }
    fun addSelected() { val candidate=state.value.pending ?: return; dismissSelection(); capture(false) { listOf(candidate) } }
    fun collected(raw: String?, page: String) = capture(true) {
        require(raw!=null && raw.length<=1100000)
        val decoded=JSONTokener(raw).nextValue(); val array=JSONArray(if(decoded is String) decoded else raw)
        (0 until minOf(array.length(),80)).mapNotNull { i -> array.optJSONObject(i)?.let { item ->
            val options=item.optJSONArray("options")
            BrowserCandidate(item.optString("src"),page,item.optString("title"),item.optString("anchor"),item.optInt("width"),item.optInt("height"),
                (0 until minOf(options?.length() ?: 0,16)).mapNotNull { j -> options?.optJSONObject(j)?.let {
                    BrowserImageOption(it.optString("url"),it.optInt("width"),it.optInt("height"),it.optString("kind","Page image"))
                } })
        } }
    }
    private fun capture(replace: Boolean, read: () -> List<BrowserCandidate>) {
        generation++; val ticket=generation; captureJob?.cancel()
        mutable.update { it.copy(collecting=true,showImages=true,items=if(replace) emptyList() else it.items,selected=emptySet()) }
        captureJob=viewModelScope.launch {
            try {
                val found=withContext(Dispatchers.IO) { read() }; var skipped=0
                for(candidate in found.take(80)) {
                    ensureActive(); if(state.value.items.size>=BrowserImagePolicy.MAX_TRAY) break
                    try {
                        val photo=withContext(Dispatchers.IO) { BrowserImageStore.capture(getApplication(),candidate) }
                        val options=BrowserImagePolicy.alternatives(candidate)
                        if(ticket==generation && state.value.items.none { it.photo.highResUrl==photo.highResUrl })
                            mutable.update { it.copy(items=it.items+BrowserDetectedImage(photo,options)) }
                    } catch(cancel: CancellationException){throw cancel} catch(error: Exception){skipped++}
                }
                if(ticket==generation && skipped>0) message("$skipped unsupported or invalid image(s) skipped")
            } catch(cancel: CancellationException){throw cancel}
            catch(error: Exception){if(ticket==generation)message("This page could not be scanned. Try opening an image or its source page.")}
            finally { if(ticket==generation) mutable.update { it.copy(collecting=false) } }
        }
    }
    fun importSelected() {
        val images=state.value.items.filter { it.photo.id in state.value.selected }.map { it.photo }
        if(images.isEmpty()) return
        mutable.update { it.copy(batch=images.map { photo -> photo.id }.toSet(),selected=emptySet(),queueErrors=emptyMap()) }
        viewModelScope.launch {
            for(photo in images) try { BrowserImageDownloads.enqueue(getApplication(),photo) }
            catch(cancel: CancellationException){throw cancel}
            catch(error: Exception){mutable.update { it.copy(queueErrors=it.queueErrors+(photo.id to "Could not queue import")) } }
        }
    }
    fun clearTray() { generation++;captureJob?.cancel();mutable.update { it.copy(items=emptyList(),selected=emptySet(),pending=null,collecting=false) } }
    fun save(photo: PhotoItem) { viewModelScope.launch {
        try { BrowserImageDownloads.enqueue(getApplication(),photo);message("Import queued; completion appears in Saved and Downloads") }
        catch(cancel: CancellationException){throw cancel}catch(error: Exception){message("Could not queue import")}
    } }
}
