package com.sandeep.imghunt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.sandeep.imghunt.engine.*

@Composable
fun NativeSearchScreen(state: NativeSearchState, loader: ImageLoader, grid: LazyGridState,
    onQuery: (String) -> Unit, onSearch: () -> Unit, onCancel: () -> Unit, onMode: (SearchMode) -> Unit,
    onSettings: () -> Unit, onMore: () -> Unit, onOpen: (PhotoItem) -> Unit,
    onFavorite: (PhotoItem) -> Unit, onDownload: (PhotoItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Free catalog search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        Text("For Google Images and other websites, use Browse.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(state.query, onQuery, singleLine = true, label = { Text("Search images") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = { IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search images") } })
        Row {
            TextButton(onClick = onSettings) { Text("Free sources") }
            if (state.loading || state.loadingMore) TextButton(onClick = onCancel) { Text("Cancel") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        if (state.error != null) TextButton(onClick = onSearch) { Text("Retry") }
        if (state.note.isNotBlank()) Text(state.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        if (state.images.isNotEmpty() || state.hasMore) NativeImageGrid(state.images, state.favorites, loader, grid,
            onOpen, onFavorite, onDownload, state.hasMore, state.loadingMore, onMore, Modifier.weight(1f))
        else if (!state.loading) Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(if (state.hasSearched) "No matching catalog images. Try another term, relax filters, or use Browse." else "Search the selected free public catalogs. No API key needed.")
        }
    }
}
