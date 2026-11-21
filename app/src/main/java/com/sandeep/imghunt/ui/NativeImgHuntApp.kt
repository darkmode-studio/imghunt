package com.sandeep.imghunt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandeep.imghunt.data.*
import com.sandeep.imghunt.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeImgHuntApp(vm: NativeSearchViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loader = remember { PhotoSources.createImageLoader(context) }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val downloadFlow = remember { ImgHuntDb.get(context).dao().observeAll() }
    val downloads by downloadFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable { mutableStateOf("Search") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var viewerUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerSaved by rememberSaveable { mutableStateOf(false) }
    val searchGrid = rememberLazyGridState()
    val savedGrid = rememberLazyGridState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = vm.pendingDownload
        vm.pendingDownload = null
        if (granted && pending != null) vm.download(pending)
        else if (!granted) vm.notice("Storage permission is needed to save images on this Android version")
    }
    val download: (PhotoItem) -> Unit = { photo ->
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            vm.pendingDownload = photo
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.download(photo)
    }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); vm.notice(null) } }
    val viewerPhotos = if (viewerSaved) state.favorites else state.images
    val index = viewerPhotos.indexOfFirst { it.highResUrl == viewerUrl }
    LaunchedEffect(viewerUrl, viewerPhotos) {
        if (viewerUrl != null && viewerPhotos.none { it.highResUrl == viewerUrl }) viewerUrl = null
    }
    if (index >= 0) {
        val photo = viewerPhotos[index]
        BackHandler { viewerUrl = null }
        PixViewerSheet(photo, index, viewerPhotos.size, state.favorites.any { it.highResUrl == photo.highResUrl }, loader,
            onBack = { viewerUrl = null }, onPrev = { viewerPhotos.getOrNull(index - 1)?.let { viewerUrl = it.highResUrl } },
            onNext = { viewerPhotos.getOrNull(index + 1)?.let { viewerUrl = it.highResUrl } },
            onToggleFav = { vm.favorite(photo) }, onDownloadHq = download)
        return
    }
    BackHandler(enabled = tab != "Search") { tab = "Search" }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("ImgHunt") }, actions = {
            if (tab == "Search") IconButton(onClick = { filters = true }) { Icon(Icons.Outlined.Tune, contentDescription = "Image filters") }
            IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Search settings") }
        })
    }, bottomBar = {
        NavigationBar {
            listOf("Search" to Icons.Outlined.Search, "Saved" to Icons.Outlined.FavoriteBorder, "Downloads" to Icons.Outlined.Download).forEach { (name, icon) ->
                NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = { Icon(icon, contentDescription = null) }, label = { Text(name) })
            }
        }
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "Search" -> NativeSearchScreen(state, loader, searchGrid, vm::query, vm::search, vm::cancel, vm::mode,
                    { settings = true }, vm::more, { viewerSaved = false; viewerUrl = it.highResUrl }, vm::favorite, download)
                "Saved" -> if (state.favorites.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Save images with the heart button to find them here.")
                } else NativeImageGrid(state.favorites, state.favorites, loader, savedGrid,
                    { viewerSaved = true; viewerUrl = it.highResUrl }, vm::favorite, download)
                else -> NativeDownloads(downloads,
                    onOpen = { record -> runCatching { openNativeDownloadedImage(context, record) }.onFailure { vm.notice("Could not open the saved image") } },
                    onRetry = { record -> scope.launch {
                        try { DownloadPipeline.retryDownload(context, record) } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { vm.notice("Could not retry the download") }
                    } },
                    onCancel = { record -> scope.launch {
                        try { DownloadPipeline.cancelDownload(context, record) } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { vm.notice("Could not cancel the download") }
                    } })
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        NativeSearchSettings(state, vm::saveGateway, vm::provider, vm::key)
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }) {
        var draft by remember { mutableStateOf(state.filters) }
        PixFilterSheet(draft, { draft = it }, { vm.filters(draft); filters = false; vm.search() })
    }
}
