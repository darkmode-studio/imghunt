package com.sandeep.imghunt.ui

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.sandeep.imghunt.engine.AspectFilter
import com.sandeep.imghunt.engine.FormatFilter
import com.sandeep.imghunt.engine.LicenseFilter
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.ProviderResult
import com.sandeep.imghunt.engine.ResolutionFilter
import com.sandeep.imghunt.engine.SearchFilters
import com.sandeep.imghunt.engine.SortOrder
import com.sandeep.imghunt.engine.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun PixViewerSheet(
    photo: PhotoItem,
    index: Int,
    total: Int,
    isFavourite: Boolean,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFav: () -> Unit,
    onDownloadHq: (PhotoItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        }
                    )
                }
                .pointerInput(photo.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale <= 1f) Offset.Zero else offset + pan
                    }
                }
        ) {
            AsyncImage(
                model = photo.highResUrl.ifBlank { photo.previewUrl },
                imageLoader = imageLoader,
                contentDescription = photo.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape, modifier = Modifier.clickable { onBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.padding(10.dp).size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                    Text("${index + 1} / $total", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onPrev, enabled = index > 0) { Text("Prev", color = Color.White) }
                TextButton(onClick = onNext, enabled = index < total - 1) { Text("Next", color = Color.White) }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (isFavourite) ImgColors.AccentSoft else Color.White
                    )
                }
            }
        }

        Surface(color = ImgColors.SurfaceCard, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(photo.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ImgColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val meta = buildList {
                    add(photo.provider.label)
                    if (photo.width > 0 && photo.height > 0) add("${photo.width}x${photo.height}")
                    val ext = photo.inferredExtension().uppercase()
                    if (ext != "UNKNOWN") add(ext)
                    add(photo.licenseLabel)
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = ImgColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!photo.creator.isNullOrBlank()) {
                    Text("Credit: ${photo.creator}", style = MaterialTheme.typography.labelSmall, color = ImgColors.AccentSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onDownloadHq(photo) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ImgColors.AccentViolet, contentColor = ImgColors.OnAccent)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download HQ", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, photo.attributionText())
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "Share image")) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", color = ImgColors.TextPrimary, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val bytes = URL(photo.highResUrl.ifBlank { photo.previewUrl }).openStream().use { it.readBytes() }
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Invalid image")
                                        WallpaperManager.getInstance(context).setBitmap(bmp)
                                    }.isSuccess
                                }
                                Toast.makeText(context, if (ok) "Wallpaper updated" else "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                    ) {
                        Icon(Icons.Outlined.Wallpaper, contentDescription = null, tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Wallpaper", color = ImgColors.TextPrimary, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("imghunt_attribution", photo.attributionText()))
                            Toast.makeText(context, "Copied link & credit", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ImgColors.BorderSubtle)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = ImgColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = ImgColors.TextPrimary, maxLines = 1)
                    }
                }

                if (photo.sourcePageUrl.startsWith("http")) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.sourcePageUrl))) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = ImgColors.AccentSoft, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open source page", color = ImgColors.AccentSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun PixFilterSheet(
    filters: SearchFilters,
    onUpdate: (SearchFilters) -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filters & Sort", style = MaterialTheme.typography.titleLarge, color = ImgColors.TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { onUpdate(SearchFilters()) }) {
                Text("Reset", color = ImgColors.AccentSoft)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Sort by", style = MaterialTheme.typography.titleSmall, color = ImgColors.TextMuted)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortOrder.entries.forEach { so ->
                FilterChip(selected = filters.sort == so, onClick = { onUpdate(filters.copy(sort = so)) }, label = { Text(so.label) })
            }
        }
        Spacer(Modifier.height(10.dp))
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
fun PixSourcesSheet(
    enabledProviders: Set<SourceProvider>,
    apiKeys: Map<SourceProvider, String>,
    providerStates: List<ProviderResult>,
    onToggleProvider: (SourceProvider, Boolean) -> Unit,
    onSaveKey: (SourceProvider, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text("Sources & API Keys", style = MaterialTheme.typography.titleLarge, color = ImgColors.TextPrimary)
        Text("Free keys can be created on Unsplash, Pexels, or Pixabay developer dashboards.", style = MaterialTheme.typography.bodySmall, color = ImgColors.TextMuted)
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
