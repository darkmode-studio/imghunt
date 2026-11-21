package com.sandeep.imghunt.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.sandeep.imghunt.engine.PhotoItem

@Composable
fun BrowserImageSelectionScreen(state: BrowserState, loader: ImageLoader, onBack: () -> Unit,
    onToggle: (String) -> Unit, onAll: () -> Unit, onChoose: (String,Int) -> Unit,
    onPreview: (PhotoItem) -> Unit, onFavorite: (PhotoItem) -> Unit,
    onImport: () -> Unit, onSource: (String) -> Unit) {
    Surface(modifier=Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Return to browser") }
                Column(Modifier.weight(1f)) {
                    Text("Images from this page",style=MaterialTheme.typography.titleLarge)
                    Text("${state.items.size} detected · ${state.selected.size} selected",style=MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick=onAll,enabled=state.items.isNotEmpty()) {
                    Text(if(state.selected.size==state.items.size) "Deselect" else "Select all")
                }
            }
            if(state.batch.isNotEmpty()) {
                val done=state.batch.count { state.transfers[it]?.status=="done" }
                val failed=state.batch.count { state.transfers[it]?.status=="failed" || it in state.queueErrors }
                Text("$done / ${state.batch.size} imported · $failed failed",Modifier.padding(horizontal=16.dp))
                if(done+failed<state.batch.size) LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
            }
            when {
                state.collecting -> Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                    Text("No supported images found. Open an image or its source page, let it load, then tap Find Images again. Some websites restrict access.")
                }
                else -> {
                    LazyVerticalGrid(columns=GridCells.Adaptive(160.dp),modifier=Modifier.weight(1f).fillMaxWidth(),
                        contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(state.items,key={it.photo.id}) { row ->
                            BrowserDetectedCard(row,state,loader,onToggle,onChoose,onPreview,onFavorite,onSource)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Resolution hints are verified only after download. Preview-only images are not enlarged or silently substituted.",
                Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)
            Button(onClick=onImport,enabled=state.selected.isNotEmpty()&&!state.collecting,
                modifier=Modifier.fillMaxWidth().padding(12.dp).heightIn(min=48.dp)) {
                Text("Import to ImgHunt (${state.selected.size})")
            }
        }
    }
}

@Composable
private fun BrowserDetectedCard(row: BrowserDetectedImage, state: BrowserState, loader: ImageLoader,
    onToggle: (String) -> Unit, onChoose: (String,Int) -> Unit, onPreview: (PhotoItem) -> Unit,
    onFavorite: (PhotoItem) -> Unit, onSource: (String) -> Unit) {
    val photo=row.photo
    val transfer=state.transfers[photo.id]
    var choices by remember(photo.id) { mutableStateOf(false) }
    ElevatedCard(modifier=Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsyncImage(model=photo.previewUrl,imageLoader=loader,contentDescription=photo.title,
                    contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Checkbox(checked=photo.id in state.selected,onCheckedChange={onToggle(photo.id)},modifier=Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(photo.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                Text(row.quality,style=MaterialTheme.typography.labelMedium,
                    color=if(row.quality=="Preview only") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(photo.sourceLabel,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                if(row.options.isNotEmpty()) {
                    Box {
                        TextButton(onClick={choices=true}) {
                            Text(if(row.options.size>1) "Choose resolution (${row.options.size})" else row.options[0].kind)
                        }
                        DropdownMenu(expanded=choices,onDismissRequest={choices=false}) {
                            row.options.forEachIndexed { index,option ->
                                val hint=if(option.width>0) " · ${option.width}w hint" else " · size unknown"
                                DropdownMenuItem(text={Text(option.kind+hint)},onClick={onChoose(photo.id,index);choices=false})
                            }
                        }
                    }
                }
                val status=state.queueErrors[photo.id] ?: when(transfer?.status) {
                    "done" -> "Imported — saved on device"
                    "running" -> "Importing ${transfer.bytesDone/1024} KB"
                    "queued" -> "Queued — waiting to import"
                    "failed" -> transfer.error ?: "Import failed; select again to retry"
                    else -> null
                }
                status?.let { Text(it,style=MaterialTheme.typography.labelSmall,
                    color=if(transfer?.status=="failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                if(transfer?.status=="running") LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick={onPreview(photo)}) { Text("Preview") }
                    TextButton(onClick={onFavorite(photo)}) { Text("Bookmark") }
                }
                TextButton(onClick={onSource(photo.sourcePageUrl)}) { Text("Open source page") }
            }
        }
    }
}
