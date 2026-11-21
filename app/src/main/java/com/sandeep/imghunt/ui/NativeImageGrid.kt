package com.sandeep.imghunt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sandeep.imghunt.engine.PhotoItem

@Composable
fun NativeImageGrid(images: List<PhotoItem>, favorites: List<PhotoItem>, imageLoader: ImageLoader,
    gridState: LazyGridState, onOpen: (PhotoItem) -> Unit, onFavorite: (PhotoItem) -> Unit,
    onDownload: (PhotoItem) -> Unit, hasMore: Boolean = false, loadingMore: Boolean = false,
    onMore: () -> Unit = {}, modifier: Modifier = Modifier) {
    val saved = remember(favorites) { favorites.map { it.highResUrl }.toSet() }
    LazyVerticalGrid(columns = GridCells.Adaptive(156.dp), state = gridState, modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(images, key = { it.highResUrl }, contentType = { "image" }) { photo ->
            NativeImageCard(photo, photo.highResUrl in saved, imageLoader,
                { onOpen(photo) }, { onFavorite(photo) }, { onDownload(photo) })
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "continuation") {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                when {
                    loadingMore -> CircularProgressIndicator(Modifier.size(28.dp))
                    hasMore -> OutlinedButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show more images") }
                    images.isNotEmpty() -> Text("End of available results", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun NativeImageCard(photo: PhotoItem, favorite: Boolean, loader: ImageLoader,
    onOpen: () -> Unit, onFavorite: () -> Unit, onDownload: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(photo.highResUrl) { mutableStateOf(true) }
    var failed by remember(photo.highResUrl) { mutableStateOf(false) }
    val request = remember(photo.previewUrl) { ImageRequest.Builder(context).data(photo.previewUrl).size(600).build() }
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            AsyncImage(model = request, imageLoader = loader, contentDescription = photo.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onLoading = { loading = true; failed = false }, onSuccess = { loading = false },
                onError = { loading = false; failed = true })
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null)
                Text("Preview unavailable", style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(photo.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(photo.sourceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photo.width > 0 && photo.height > 0) Text("${photo.width} × ${photo.height}", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorite) "Remove saved image" else "Save image")
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = "Download image")
            }
        }
    }
}
