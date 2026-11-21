package com.sandeep.imghunt.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sandeep.imghunt.data.AppPrefs
import com.sandeep.imghunt.data.DownloadPipeline
import com.sandeep.imghunt.data.DownloadRecord
import com.sandeep.imghunt.data.ImgHuntDb
import com.sandeep.imghunt.engine.AspectFilter
import com.sandeep.imghunt.engine.FormatFilter
import com.sandeep.imghunt.engine.LicenseFilter
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.engine.ProviderResult
import com.sandeep.imghunt.engine.ResolutionFilter
import com.sandeep.imghunt.engine.SearchFilters
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.launch

private enum class HuntTab(val label: String) {
    SEARCH("Hunt"),
    DOWNLOADS("Downloads")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImgHuntApp() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val imageLoader = remember { PhotoSources.createImageLoader(context) }

    val prefs = remember { AppPrefs(context) }
    val db = remember { ImgHuntDb.get(context) }
    val downloads by db.dao().observeAll().collectAsState(initial = emptyList())

    var tab by remember { mutableStateOf(HuntTab.SEARCH) }
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters(resolution = ResolutionFilter.ANY)) }
    var enabledProviders by remember { mutableStateOf(prefs.loadEnabledProviders()) }
    var apiKeys by remember { mutableStateOf(prefs.loadKeys()) }

    var results by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var providerStates by remember { mutableStateOf<List<ProviderResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activePhotoIndex by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val selectionMode = selectedIds.isNotEmpty()

    fun toggleSelect(item: PhotoItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
    }

    fun runSearch(customQuery: String? = null) {
        val q = (customQuery ?: query).trim()
        if (q.isEmpty()) {
            statusBanner = "Type a keyword to search high-res photos"
            return
        }
        if (customQuery != null) query = q
        selectedIds = emptySet()
        keyboard?.hide()
        isSearching = true
        statusBanner = null
        scope.launch {
            val (found, states) = PhotoSources.searchWithFallback(q, filters, enabledProviders, apiKeys)
            results = found
            providerStates = states
            isSearching = false
            val fallbackUsed = states.any { it.usedFallback && it.items.isNotEmpty() }
            statusBanner = when {
                found.isEmpty() -> "No matching images returned"
                fallbackUsed -> "${found.size} results (fallback active)"
                else -> "${found.size} results"
            }
        }
    }

    fun downloadBatch(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        items.forEach { DownloadPipeline.enqueueHighRes(context, it) }
        Toast.makeText(context, "Queued ${items.size} HQ download${if (items.size > 1) "s" else ""}", Toast.LENGTH_SHORT).show()
        selectedIds = emptySet()
    }

    val selectedPhoto = activePhotoIndex?.let { idx -> results.getOrNull(idx) }
    if (selectedPhoto != null && activePhotoIndex != null) {
        BackHandler { activePhotoIndex = null }
        PixViewerSheet(
            photo = selectedPhoto,
            index = activePhotoIndex!!,
            total = results.size,
            imageLoader = imageLoader,
            onBack = { activePhotoIndex = null },
            onPrev = { if (activePhotoIndex!! > 0) activePhotoIndex = activePhotoIndex!! - 1 },
            onNext = { if (activePhotoIndex!! < results.size - 1) activePhotoIndex = activePhotoIndex!! + 1 },
            onDownloadHq = { item -> downloadBatch(listOf(item)) }
        )
        return
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

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
                    if (selectionMode) {
                        Surface(
                            color = ImgColors.SurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ImgColors.AccentViolet.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedIds = emptySet() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = ImgColors.TextPrimary)
                                }
                                Text(
                                    text = "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = ImgColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        selectedIds = if (selectedIds.size == results.size) emptySet() else results.map { it.id }.toSet()
                                    }
                                ) {
                                    Icon(Icons.Outlined.SelectAll, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (selectedIds.size == results.size) "Clear" else "All", color = ImgColors.AccentSoft)
                                }
                                Button(
                                    onClick = { downloadBatch(results.filter { it.id in selectedIds }) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ImgColors.AccentViolet, contentColor = ImgColors.OnAccent)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("HQ (${selectedIds.size})")
                                }
                            }
                        }
                    } else if (tab == HuntTab.SEARCH) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search high-res images…", color = ImgColors.TextMuted) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = ImgColors.AccentSoft) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (query.isNotBlank()) {
                                        IconButton(onClick = { query = ""; results = emptyList(); statusBanner = null }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear query", tint = ImgColors.TextMuted)
                                        }
                                    }
                                    TextButton(onClick = { runSearch() }) {
                                        Text("Search", color = ImgColors.AccentSoft, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ResolutionFilter.entries.forEach { rf ->
                                    FilterChip(
                                        selected = filters.resolution == rf,
                                        onClick = {
                                            filters = filters.copy(resolution = rf)
                                            if (query.isNotBlank() && results.isNotEmpty()) runSearch()
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
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${enabledProviders.size} APIs", style = MaterialTheme.typography.labelMedium, color = ImgColors.AccentSoft)
                                }
                            }
                        }

                        if (statusBanner != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = statusBanner!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (results.isEmpty() && !isSearching) ImgColors.Warning else ImgColors.TextMuted
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Downloads", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ImgColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text("Pictures/ImgHunt", style = MaterialTheme.typography.labelMedium, color = ImgColors.TextMuted)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = ImgColors.SurfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HuntTab.entries.forEach { t ->
                        val active = tab == t
                        val label = if (t == HuntTab.DOWNLOADS && downloads.isNotEmpty()) "${t.label} (${downloads.size})" else t.label
                        TextButton(
                            onClick = { tab = t },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (active) ImgColors.AccentSoft else ImgColors.TextMuted
                            )
                        ) {
                            Icon(
                                imageVector = if (t == HuntTab.SEARCH) Icons.Outlined.ImageSearch else Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ImgColors.AccentViolet,
                    trackColor = ImgColors.SurfaceElevated
                )
            }

            when (tab) {
                HuntTab.SEARCH -> {
                    if (results.isEmpty() && !isSearching) {
                        PixEmptyState(onPickPreset = { preset -> runSearch(preset) })
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 170.dp),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(results, key = { _, item -> item.id }) { idx, item ->
                                val isSelected = item.id in selectedIds
                                PixPhotoTile(
                                    item = item,
                                    selected = isSelected,
                                    selectionMode = selectionMode,
                                    imageLoader = imageLoader,
                                    onTap = {
                                        if (selectionMode) toggleSelect(item) else activePhotoIndex = idx
                                    },
                                    onLongPress = { toggleSelect(item) },
                                    onDownloadHq = { downloadBatch(listOf(item)) }
                                )
                            }
                        }
                    }
                }

                HuntTab.DOWNLOADS -> {
                    DownloadsTabContent(
                        records = downloads,
                        onClearFinished = { scope.launch { db.dao().clearFinished() } }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = ImgColors.SurfaceCard,
            contentColor = ImgColors.TextPrimary
        ) {
            PixFilterSheet(
                filters = filters,
                onUpdate = { filters = it },
                onApply = {
                    showFilterSheet = false
                    if (query.isNotBlank()) runSearch()
                }
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = ImgColors.SurfaceCard,
            contentColor = ImgColors.TextPrimary
        ) {
            PixSourcesSheet(
                enabledProviders = enabledProviders,
                apiKeys = apiKeys,
                providerStates = providerStates,
                onToggleProvider = { p, enabled ->
                    val next = if (enabled) enabledProviders + p else enabledProviders - p
                    enabledProviders = next
                    prefs.saveEnabledProviders(next)
                },
                onSaveKey = { p, key ->
                    val nextKeys = apiKeys.toMutableMap().apply {
                        if (key.isBlank()) remove(p) else put(p, key.trim())
                    }
                    apiKeys = nextKeys
                    prefs.saveKey(p, key)
                }
            )
        }
    }
}

@Composable
private fun PixEmptyState(onPickPreset: (String) -> Unit) {
    val suggestions = listOf("Kerala hills", "Street photography", "Minimalist architecture", "Night sky")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ImgColors.SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ImageSearch, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("imghunt", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = ImgColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Fast structured image search with automatic source fallback & direct HQ download.", style = MaterialTheme.typography.bodyMedium, color = ImgColors.TextMuted)
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        ImageRequest.Builder(context)
            .data(currentUrl)
            .size(720)
            .build()
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
            .combinedClickable(
                onClick = { onTap() },
                onLongClick = { onLongPress() }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .background(ImgColors.SurfaceElevated)
        ) {
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
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ImgColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val sub = buildList {
                add(item.provider.label)
                if (item.width > 0 && item.height > 0) add("${item.width}x${item.height}")
                add(item.inferredExtension().uppercase())
            }.joinToString(" · ")
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = ImgColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PixViewerSheet(
    photo: PhotoItem,
    index: Int,
    total: Int,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDownloadHq: (PhotoItem) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AsyncImage(
                model = photo.highResUrl.ifBlank { photo.previewUrl },
                imageLoader = imageLoader,
                contentDescription = photo.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${index + 1} / $total",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onPrev, enabled = index > 0) { Text("Prev", color = Color.White) }
                TextButton(onClick = onNext, enabled = index < total - 1) { Text("Next", color = Color.White) }
            }
        }

        Surface(
            color = ImgColors.SurfaceCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = photo.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ImgColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    add(photo.provider.label)
                    if (photo.width > 0 && photo.height > 0) add("${photo.width}x${photo.height}")
                    add(photo.inferredExtension().uppercase())
                    add(photo.licenseLabel)
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = ImgColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onDownloadHq(photo) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ImgColors.AccentViolet, contentColor = ImgColors.OnAccent)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download HQ", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("imghunt_url", photo.highResUrl))
                            Toast.makeText(context, "Copied HQ link", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy link", color = ImgColors.TextPrimary)
                    }
                    if (photo.sourcePageUrl.startsWith("http")) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.sourcePageUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                        ) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Source page", color = ImgColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixFilterSheet(
    filters: SearchFilters,
    onUpdate: (SearchFilters) -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, color = ImgColors.TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { onUpdate(SearchFilters(resolution = ResolutionFilter.ANY)) }) {
                Text("Reset", color = ImgColors.AccentSoft)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Resolution", style = MaterialTheme.typography.titleSmall, color = ImgColors.TextMuted)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolutionFilter.entries.forEach { rf ->
                FilterChip(selected = filters.resolution == rf, onClick = { onUpdate(filters.copy(resolution = rf)) }, label = { Text(rf.label) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Shape", style = MaterialTheme.typography.titleSmall, color = ImgColors.TextMuted)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectFilter.entries.forEach { af ->
                FilterChip(selected = filters.aspect == af, onClick = { onUpdate(filters.copy(aspect = af)) }, label = { Text(af.label) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Format & Rights", style = MaterialTheme.typography.titleSmall, color = ImgColors.TextMuted)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatFilter.entries.forEach { ff ->
                FilterChip(selected = filters.format == ff, onClick = { onUpdate(filters.copy(format = ff)) }, label = { Text(ff.label) })
            }
            LicenseFilter.entries.forEach { lf ->
                FilterChip(selected = filters.license == lf, onClick = { onUpdate(filters.copy(license = lf)) }, label = { Text(lf.label) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ImgColors.AccentViolet, contentColor = ImgColors.OnAccent)
        ) {
            Text("Show results", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PixSourcesSheet(
    enabledProviders: Set<SourceProvider>,
    apiKeys: Map<SourceProvider, String>,
    providerStates: List<ProviderResult>,
    onToggleProvider: (SourceProvider, Boolean) -> Unit,
    onSaveKey: (SourceProvider, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text("Sources & API Keys", style = MaterialTheme.typography.titleLarge, color = ImgColors.TextPrimary)
        Text("Open sources run automatically; add optional keys for Unsplash, Pexels, or Pixabay.", style = MaterialTheme.typography.bodySmall, color = ImgColors.TextMuted)
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.height(420.dp)) {
            items(SourceProvider.entries, key = { it.name }) { p ->
                val state = providerStates.find { it.provider == p }
                var draftKey by remember(p, apiKeys[p]) { mutableStateOf(apiKeys[p].orEmpty()) }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.label, style = MaterialTheme.typography.titleSmall, color = ImgColors.TextPrimary)
                                if (p.requiresKey) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = ImgColors.TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                            val sub = when {
                                state?.error != null -> "Error: ${state.error}"
                                state != null -> "${state.items.size} found${if (state.usedFallback) " · fallback" else ""}"
                                p.requiresKey -> "Optional API key"
                                else -> "Open API"
                            }
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = if (state?.error != null) ImgColors.Danger else ImgColors.TextMuted)
                        }
                        Switch(
                            checked = p in enabledProviders,
                            onCheckedChange = { onToggleProvider(p, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = ImgColors.AccentViolet)
                        )
                    }
                    if (p.requiresKey) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draftKey,
                                onValueChange = { draftKey = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("${p.label} key", color = ImgColors.TextMuted) }
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { onSaveKey(p, draftKey) }) {
                                Text("Save", color = ImgColors.AccentSoft)
                            }
                        }
                    }
                    HorizontalDivider(color = ImgColors.BorderSubtle, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DownloadsTabContent(
    records: List<DownloadRecord>,
    onClearFinished: () -> Unit
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
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    else -> "${rec.provider} · ${rec.status}"
                                }
                                Text(info, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
