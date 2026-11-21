package com.sandeep.imghunt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sandeep.imghunt.engine.SourceProvider

@Composable
fun NativeSearchSettings(state: NativeSearchState, onProvider: (SourceProvider, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Free sources and browser", style = MaterialTheme.typography.headlineSmall)
        Text("No gateway, access token, paid API or account is required by ImgHunt.")
        Text("Browse opens websites in an app-local WebView, not your Chrome profile. Google may require an external browser for sign-in. Site limits and image rights still apply.")
        Text("Long-press an image or tap Collect loaded images. The temporary tray is not permanent storage; tap Download to keep a local file.")
        Text("Downloads do not copy browser cookies. Private or login-protected images may not download.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Free catalog sources", style = MaterialTheme.typography.titleMedium)
        listOf(SourceProvider.OPENVERSE, SourceProvider.WIKIMEDIA, SourceProvider.ARCHIVE, SourceProvider.NASA, SourceProvider.MET).forEach { provider ->
            val result = state.states.find { it.provider == provider }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(provider.label)
                    Text(result?.error ?: result?.let { "${it.items.size} returned on the last page" } ?: "Public catalog; availability and limits apply",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(provider in state.providers, { onProvider(provider, it) })
            }
        }
        Text("Catalogs are narrower than a whole-web search. Use Browse for visible, user-directed website searches.", style = MaterialTheme.typography.bodySmall)
    }
}
