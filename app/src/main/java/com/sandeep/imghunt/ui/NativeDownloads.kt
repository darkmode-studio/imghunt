package com.sandeep.imghunt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sandeep.imghunt.data.DownloadRecord

@Composable
fun NativeDownloads(records: List<DownloadRecord>, onOpen: (DownloadRecord) -> Unit,
    onRetry: (DownloadRecord) -> Unit, onCancel: (DownloadRecord) -> Unit) {
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No downloads yet. Use the download button on an image. Saved files go to Pictures/ImgHunt.")
        }
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(records, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(record.provider, style = MaterialTheme.typography.labelSmall)
                        Text(when (record.status) {
                            "done" -> "Saved to Pictures/ImgHunt"
                            "failed" -> record.error ?: "Download failed"
                            "running" -> "Downloading ${record.bytesDone / 1024} KB"
                            else -> "Queued; waiting for a connection"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    when (record.status) {
                        "done" -> IconButton(onClick = { onOpen(record) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open downloaded image")
                        }
                        "failed" -> IconButton(onClick = { onRetry(record) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Retry download")
                        }
                        else -> IconButton(onClick = { onCancel(record) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                        }
                    }
                }
                if (record.status == "running" || record.status == "queued") {
                    Spacer(Modifier.height(12.dp))
                    if (record.bytesTotal > 0) LinearProgressIndicator(
                        progress = { (record.bytesDone.toFloat() / record.bytesTotal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } }
        }
    }
}
