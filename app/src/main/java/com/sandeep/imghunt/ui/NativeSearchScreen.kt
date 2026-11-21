package com.sandeep.imghunt.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.sandeep.imghunt.engine.*

@Composable
fun NativeSearchScreen(state: NativeSearchState, loader: ImageLoader, grid: LazyGridState,
    onQuery: (String) -> Unit, onSearch: () -> Unit, onCancel: () -> Unit,
    onMode: (SearchMode) -> Unit, onSettings: () -> Unit, onMore: () -> Unit,
    onOpen: (PhotoItem) -> Unit, onFavorite: (PhotoItem) -> Unit, onDownload: (PhotoItem) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); onSearch() }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = state.query, onValueChange = onQuery,
            label = { Text("Search images") }, placeholder = { Text("People, places, products…") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Outlined.Search, contentDescription = "Search images") } })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchMode.entries.forEach { mode ->
                FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
            }
            if (state.loading || state.loadingMore) TextButton(onClick = onCancel) { Text("Cancel") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (!state.hasSearched && state.history.isNotEmpty()) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.history.take(5).forEach { term ->
                SuggestionChip(onClick = { onQuery(term); submit() }, label = { Text(term.take(28)) })
            }
        }
        if (state.mode == SearchMode.WEB && state.setupRequired) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connect broader web search", style = MaterialTheme.typography.titleMedium)
                Text(state.error ?: "Add your HTTPS gateway and app access token. Your provider key stays on the server.")
                Button(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Search settings") }
                Text("No gateway yet? Open catalogs remains available without one.", style = MaterialTheme.typography.bodySmall)
            } }
        } else if (state.error != null) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(state.error)
                    Row {
                        TextButton(onClick = { if (state.images.isNotEmpty() && state.hasMore) onMore() else submit() }) { Text("Retry") }
                        TextButton(onClick = onSettings) { Text("Settings") }
                    }
                }
            }
        }
        if (state.note.isNotBlank()) Text(state.note, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (state.images.isNotEmpty() || state.hasMore) {
            NativeImageGrid(state.images, state.favorites, loader, grid, onOpen, onFavorite, onDownload,
                state.hasMore, state.loadingMore, onMore, Modifier.weight(1f))
        } else if (!state.loading && !state.setupRequired && state.error == null) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (state.hasSearched) "No images matched. Try another term or relax your filters."
                    else "Find an image, open it full screen, then save or download it.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
