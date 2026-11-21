package com.sandeep.imghunt.browser

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.ui.NativeImageGrid

@Composable
fun BrowserScreen(vm: BrowserViewModel, loader: ImageLoader, favorites: List<PhotoItem>,
    onOpen: (PhotoItem) -> Unit, onFavorite: (PhotoItem) -> Unit, onSave: (PhotoItem) -> Unit,
    initialUrl: String = vm.initialUrl, onWebViewReady: (WebView) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var epoch by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    LaunchedEffect(state.images.size) { if (state.images.isNotEmpty()) expanded = true }
    BackHandler(enabled = state.canBack && web != null) { web?.goBack() }
    val go: () -> Unit = {
        runCatching { BrowserImagePolicy.address(state.query) }.onSuccess { address ->
            keyboard?.hide(); web?.loadUrl(address)
        }.onFailure { vm.message(it.message ?: "Enter a search term or HTTPS address") }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(state.query, vm::query, singleLine = true, label = { Text("Search Google Images or enter HTTPS URL") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }), trailingIcon = {
                IconButton(onClick = go) { Icon(Icons.Outlined.Search, contentDescription = "Search browser") }
            })
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = { web?.goBack() }, enabled = state.canBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Browser back") }
            IconButton(onClick = { web?.goForward() }, enabled = state.canForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Browser forward") }
            IconButton(onClick = { if (web == null) epoch++ else web?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload browser") }
            TextButton(onClick = {
                val source = web?.url ?: state.pageUrl
                web?.evaluateJavascript(BrowserImagePolicy.collectScript) { raw -> vm.collected(raw, source) }
            }, enabled = !state.collecting && web != null, modifier = Modifier.weight(1f)) {
                Text(if (state.collecting) "Collecting…" else "Collect loaded images", maxLines = 2)
            }
            IconButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.pageUrl))) }
                    .onFailure { vm.message("No external browser is available") }
            }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open browser") }
        }
        Text(state.pageUrl, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        if (state.progress < 100) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pane: @Composable (Modifier) -> Unit = { modifier ->
                key(epoch) { BrowserPane(vm, modifier, initialUrl) { ready -> web = ready; if (ready != null) onWebViewReady(ready) } }
            }
            val tray: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier) { Column {
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) { Text("Image tray (${state.images.size})") }
                        if (state.images.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                    }
                    if (state.images.isEmpty()) Text("Long-press an image or collect loaded images. Previews are not always originals.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    else NativeImageGrid(state.images, favorites, loader, grid, onOpen, onFavorite, onSave, modifier = Modifier.weight(1f))
                } }
            }
            if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize()) {
                pane(Modifier.weight(0.62f).fillMaxHeight()); VerticalDivider(); tray(Modifier.weight(0.38f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                pane(Modifier.weight(if (expanded && state.images.isNotEmpty()) 0.58f else 1f).fillMaxWidth())
                if (expanded && state.images.isNotEmpty()) tray(Modifier.weight(0.42f).fillMaxWidth())
                else Surface(Modifier.fillMaxWidth()) { TextButton(onClick = { expanded = true }) {
                    Text(if (state.images.isEmpty()) "Long-press images to add them to the tray" else "Open image tray (${state.images.size})")
                } }
            }
        }
    }
    state.pending?.let { AlertDialog(onDismissRequest = vm::dismissSelection,
        title = { Text("Add image to tray?") }, text = { Text("This may be a thumbnail rather than an original. Its reuse rights are unverified.") },
        confirmButton = { TextButton(onClick = vm::addSelected) { Text("Add to tray") } },
        dismissButton = { TextButton(onClick = vm::dismissSelection) { Text("Cancel") } }) }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear temporary image tray?") },
        text = { Text("This does not delete saved downloads or saved-image bookmarks.") },
        confirmButton = { TextButton(onClick = { vm.clearTray(); confirmClear = false }) { Text("Clear tray") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
