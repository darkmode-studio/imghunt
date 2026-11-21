package com.sandeep.imghunt.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sandeep.imghunt.data.DownloadPipeline
import com.sandeep.imghunt.data.DownloadRecord
import com.sandeep.imghunt.data.ImgHuntDb
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.engine.ResolutionFilter
import kotlinx.coroutines.launch

private enum class HuntTab(val label: String) {
    SEARCH("Hunt"),
    FAVOURITES("Saved"),
    DOWNLOADS("Queue")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImgHuntApp(vm: HuntViewModel = viewModel()) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val imageLoader = remember { PhotoSources.createImageLoader(context) }

    val db = remember { ImgHuntDb.get(context) }
    val downloads by db.dao().observeAll().collectAsState(initial = emptyList())

    var tab by remember { mutableStateOf(HuntTab.SEARCH) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshConnectivity() }

    val selectionMode = vm.selectedIds.isNotEmpty()
    val activePhoto = vm.activePhotoIndex?.let { idx -> vm.results.getOrNull(idx) }

    if (activePhoto != null && vm.activePhotoIndex != null) {
        BackHandler { vm.activePhotoIndex = null }
        PixViewerSheet(
            photo = activePhoto,
            index = vm.activePhotoIndex!!,
            total = vm.results.size,
            isFavourite = vm.isFavourite(activePhoto),
            imageLoader = imageLoader,
            onBack = { vm.activePhotoIndex = null },
            onPrev = { if (vm.activePhotoIndex!! > 0) vm.activePhotoIndex = vm.activePhotoIndex!! - 1 },
            onNext = { if (vm.activePhotoIndex!! < vm.results.size - 1) vm.activePhotoIndex = vm.activePhotoIndex!! + 1 },
            onToggleFav = { vm.toggleFavourite(activePhoto) },
            onDownloadHq = { item ->
                vm.enqueueBatch(listOf(item))
                Toast.makeText(context, "Queued HQ download", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    BackHandler(enabled = selectionMode) { vm.clearSelection() }

    Scaffold(
        containerColor = ImgColors.VoidBg,
        topBar = {
            Surface(color = ImgColors.VoidBg) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (vm.isOffline) {
                        Surface(
                            color = ImgColors.Warning.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = ImgColors.Warning, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Offline — check connection to search new photos", style = MaterialTheme.typography.labelMedium, color = ImgColors.Warning)
                            }
                        }
                    }

                    if (selectionMode) {
                        Surface(
                            color = ImgColors.SurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ImgColors.AccentViolet.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { vm.clearSelection() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = ImgColors.TextPrimary)
                                }
                                Text("${vm.selectedIds.size} selected", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ImgColors.TextPrimary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.toggleSelectAll() }) {
                                    Icon(Icons.Outlined.SelectAll, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (vm.selectedIds.size == vm.results.size) "Clear" else "All", color = ImgColors.AccentSoft)
                                }
                                Button(
                                    onClick = {
                                        val chosen = vm.results.filter { it.id in vm.selectedIds }
                                        vm.enqueueBatch(chosen)
                                        Toast.makeText(context, "Queued ${chosen.size} HQ downloads", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ImgColors.AccentViolet, contentColor = ImgColors.OnAccent)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("HQ (${vm.selectedIds.size})")
                                }
                            }
                        }
                    } else if (tab == HuntTab.SEARCH) {
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = { vm.query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search high-res images…", color = ImgColors.TextMuted) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = ImgColors.AccentSoft) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (vm.query.isNotBlank()) {
                                        IconButton(onClick = { vm.query = ""; vm.results = emptyList(); vm.statusBanner = null }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear query", tint = ImgColors.TextMuted)
                                        }
                                    }
                                    TextButton(onClick = { keyboard?.hide(); vm.runSearch() }) {
                                        Text("Search", color = ImgColors.AccentSoft, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.runSearch() }),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ImgColors.SurfaceCard,
                                unfocusedContainerColor = ImgColors.SurfaceCard,
                                focusedBorderColor = ImgColors.AccentViolet,
                                unfocusedBorderColor = ImgColors.BorderSubtle,
                                focusedTextColor = ImgColors.TextPrimary,
                                unfocusedTextColor = ImgColors.TextPrimary
                            )
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ResolutionFilter.entries.forEach { rf ->
                                    FilterChip(
                                        selected = vm.filters.resolution == rf,
                                        onClick = {
                                            vm.filters = vm.filters.copy(resolution = rf)
                                            if (vm.query.isNotBlank() && vm.results.isNotEmpty()) vm.runSearch()
                                        },
                                        label = { Text(rf.label) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = ImgColors.SurfaceCard,
                                            labelColor = ImgColors.TextMuted,
                                            selectedContainerColor = ImgColors.AccentViolet.copy(alpha = 0.25f),
                                            selectedLabelColor = ImgColors.TextPrimary
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = ImgColors.SurfaceCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ImgColors.BorderSubtle),
                                modifier = Modifier.clickable { showFilterSheet = true }
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Tune, contentDescription = "More filters", tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("More", style = MaterialTheme.typography.labelMedium, color = ImgColors.TextPrimary)
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = ImgColors.SurfaceCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ImgColors.BorderSubtle),
                                modifier = Modifier.clickable { showSettingsSheet = true }
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${vm.enabledProviders.size} APIs", style = MaterialTheme.typography.labelMedium, color = ImgColors.AccentSoft)
                                }
                            }
                        }

                        if (vm.history.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                vm.history.take(6).forEach { hist ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { keyboard?.hide(); vm.runSearch(hist) },
                                        label = { Text(hist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(containerColor = ImgColors.SurfaceElevated, labelColor = ImgColors.TextMuted)
                                    )
                                }
                            }
                        }

                        if (vm.statusBanner != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = vm.statusBanner!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (vm.results.isEmpty() && !vm.isSearching) ImgColors.Warning else ImgColors.TextMuted
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = if (tab == HuntTab.FAVOURITES) "Saved Favourites" else "Downloads",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ImgColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Pictures/ImgHunt", style = MaterialTheme.typography.labelMedium, color = ImgColors.TextMuted)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = ImgColors.SurfaceCard, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HuntTab.entries.forEach { t ->
                        val active = tab == t
                        val badge = when (t) {
                            HuntTab.FAVOURITES -> if (vm.favourites.isNotEmpty()) "${t.label} (${vm.favourites.size})" else t.label
                            HuntTab.DOWNLOADS -> if (downloads.isNotEmpty()) "${t.label} (${downloads.size})" else t.label
                            HuntTab.SEARCH -> t.label
                        }
                        TextButton(
                            onClick = { tab = t },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (active) ImgColors.AccentSoft else ImgColors.TextMuted)
                        ) {
                            Icon(
                                imageVector = when (t) {
                                    HuntTab.SEARCH -> Icons.Outlined.ImageSearch
                                    HuntTab.FAVOURITES -> Icons.Outlined.Favorite
                                    HuntTab.DOWNLOADS -> Icons.Outlined.Download
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(badge, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (vm.isSearching || vm.isLoadingMore) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ImgColors.AccentViolet,
                    trackColor = ImgColors.SurfaceElevated
                )
            }

            when (tab) {
                HuntTab.SEARCH -> {
                    if (vm.results.isEmpty() && !vm.isSearching) {
                        PixEmptyState(suggestions = vm.suggestions, onPickPreset = { preset -> keyboard?.hide(); vm.runSearch(preset) })
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 170.dp),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(vm.results, key = { _, item -> item.id }) { idx, item ->
                                PixPhotoTile(
                                    item = item,
                                    selected = item.id in vm.selectedIds,
                                    selectionMode = selectionMode,
                                    imageLoader = imageLoader,
                                    onTap = {
                                        if (selectionMode) vm.toggleSelect(item) else vm.activePhotoIndex = idx
                                    },
                                    onLongPress = { vm.toggleSelect(item) },
                                    onDownloadHq = {
                                        vm.enqueueBatch(listOf(item))
                                        Toast.makeText(context, "Queued HQ download", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (vm.hasMore && !vm.isSearching) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        OutlinedButton(
                                            onClick = { vm.runSearch(loadMore = true) },
                                            enabled = !vm.isLoadingMore,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                                        ) {
                                            Text(if (vm.isLoadingMore) "Loading more…" else "Load more", color = ImgColors.AccentSoft)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HuntTab.FAVOURITES -> {
                    if (vm.favourites.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No saved favourites yet.", color = ImgColors.TextMuted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 170.dp),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(vm.favourites, key = { _, item -> "fav_${item.id}" }) { _, item ->
                                PixPhotoTile(
                                    item = item,
                                    selected = false,
                                    selectionMode = false,
                                    imageLoader = imageLoader,
                                    onTap = {
                                        vm.enqueueBatch(listOf(item))
                                        Toast.makeText(context, "Queued HQ download", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongPress = { vm.toggleFavourite(item) },
                                    onDownloadHq = {
                                        vm.enqueueBatch(listOf(item))
                                        Toast.makeText(context, "Queued HQ download", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HuntTab.DOWNLOADS -> {
                    DownloadsTabContent(
                        records = downloads,
                        onClearFinished = { scope.launch { db.dao().clearFinished() } },
                        onRetry = { rec -> DownloadPipeline.retryDownload(context, rec) },
                        onCancel = { rec -> DownloadPipeline.cancelDownload(context, rec) },
                        onOpenFile = { rec ->
                            val uriStr = rec.savedUri ?: return@DownloadsTabContent
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uriStr), rec.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(context, "No viewer app found for saved file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, containerColor = ImgColors.SurfaceCard, contentColor = ImgColors.TextPrimary) {
            PixFilterSheet(
                filters = vm.filters,
                onUpdate = { vm.filters = it },
                onApply = {
                    showFilterSheet = false
                    if (vm.query.isNotBlank()) vm.runSearch()
                }
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }, containerColor = ImgColors.SurfaceCard, contentColor = ImgColors.TextPrimary) {
            PixSourcesSheet(
                enabledProviders = vm.enabledProviders,
                apiKeys = vm.apiKeys,
                providerStates = vm.providerStates,
                onToggleProvider = { p, en -> vm.toggleProvider(p, en) },
                onSaveKey = { p, k -> vm.saveApiKey(p, k) }
            )
        }
    }
}

@Composable
private fun PixEmptyState(suggestions: List<String>, onPickPreset: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(ImgColors.SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ImageSearch, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("imghunt", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = ImgColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Structured API search with automatic fallback & direct HQ download.", style = MaterialTheme.typography.bodyMedium, color = ImgColors.TextMuted)
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { label ->
                OutlinedButton(
                    onClick = { onPickPreset(label) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                ) {
                    Text(label, color = ImgColors.TextPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PixPhotoTile(
    item: PhotoItem,
    selected: Boolean,
    selectionMode: Boolean,
    imageLoader: ImageLoader,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDownloadHq: () -> Unit
) {
    val context = LocalContext.current
    val primaryUrl = item.previewUrl.ifBlank { item.highResUrl }
    var currentUrl by remember(item.id) { mutableStateOf(primaryUrl) }
    var failedPrimary by remember(item.id) { mutableStateOf(false) }

    val request = remember(currentUrl) {
        ImageRequest.Builder(context).data(currentUrl).size(720).build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ImgColors.SurfaceCard)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ImgColors.AccentViolet else ImgColors.BorderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(onClick = { onTap() }, onLongClick = { onLongPress() })
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).background(ImgColors.SurfaceElevated)) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                onError = {
                    if (!failedPrimary && item.highResUrl.isNotBlank() && currentUrl != item.highResUrl) {
                        failedPrimary = true
                        currentUrl = item.highResUrl
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) ImgColors.AccentViolet else Color.Black.copy(alpha = 0.55f))
                    .clickable { onLongPress() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Select",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ImgColors.AccentViolet)
                        .clickable { onDownloadHq() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.HighQuality, contentDescription = "Download HQ", tint = ImgColors.OnAccent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("HQ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ImgColors.OnAccent)
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ImgColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val sub = buildList {
                add(item.provider.label)
                if (item.width > 0 && item.height > 0) add("${item.width}x${item.height}")
                val ext = item.inferredExtension().uppercase()
                if (ext != "UNKNOWN") add(ext)
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.labelSmall, color = ImgColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DownloadsTabContent(
    records: List<DownloadRecord>,
    onClearFinished: () -> Unit,
    onRetry: (DownloadRecord) -> Unit,
    onCancel: (DownloadRecord) -> Unit,
    onOpenFile: (DownloadRecord) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.titleMedium, color = ImgColors.TextPrimary, modifier = Modifier.weight(1f))
            if (records.any { it.status == "done" || it.status == "failed" }) {
                TextButton(onClick = onClearFinished) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear finished", color = ImgColors.AccentSoft)
                }
            }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet. Tap HQ on any photo.", color = ImgColors.TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.id }) { rec ->
                    Surface(
                        color = ImgColors.SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ImgColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (icon, tint) = when (rec.status) {
                                    "done" -> Icons.Outlined.CheckCircle to ImgColors.Success
                                    "failed" -> Icons.Outlined.ErrorOutline to ImgColors.Danger
                                    else -> Icons.Outlined.Download to ImgColors.AccentSoft
                                }
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(rec.fileName, style = MaterialTheme.typography.bodyMedium, color = ImgColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val info = when (rec.status) {
                                        "done" -> "Saved to Pictures/ImgHunt · ${rec.provider}"
                                        "failed" -> rec.error ?: "Failed"
                                        else -> {
                                            val kbDone = rec.bytesDone / 1024
                                            val kbTot = rec.bytesTotal / 1024
                                            if (kbTot > 0) "${rec.provider} · ${kbDone}/${kbTot} KB" else "${rec.provider} · ${kbDone} KB"
                                        }
                                    }
                                    Text(info, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (rec.status == "done" && rec.savedUri != null) {
                                    IconButton(onClick = { onOpenFile(rec) }) {
                                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open file", tint = ImgColors.AccentSoft)
                                    }
                                } else if (rec.status == "failed") {
                                    IconButton(onClick = { onRetry(rec) }) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Retry", tint = ImgColors.AccentSoft)
                                    }
                                } else {
                                    IconButton(onClick = { onCancel(rec) }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = ImgColors.TextMuted)
                                    }
                                }
                            }
                            if (rec.status == "running" || rec.status == "queued") {
                                Spacer(Modifier.height(6.dp))
                                if (rec.bytesTotal > 0L) {
                                    LinearProgressIndicator(
                                        progress = { (rec.bytesDone.toFloat() / rec.bytesTotal.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ImgColors.AccentViolet,
                                        trackColor = ImgColors.SurfaceElevated
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ImgColors.AccentViolet,
                                        trackColor = ImgColors.SurfaceElevated
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
