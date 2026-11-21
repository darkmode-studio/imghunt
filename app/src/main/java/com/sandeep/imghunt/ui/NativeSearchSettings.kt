package com.sandeep.imghunt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sandeep.imghunt.engine.SourceProvider

@Composable
fun NativeSearchSettings(state: NativeSearchState, onSave: (String, String) -> Unit,
    onProvider: (SourceProvider, Boolean) -> Unit, onKey: (SourceProvider, String) -> Unit) {
    var url by rememberSaveable(state.gatewayUrl) { mutableStateOf(state.gatewayUrl) }
    var token by remember { mutableStateOf("") }
    LaunchedEffect(state.settingsRevision) { token = "" }
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
        .imePadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search settings", style = MaterialTheme.typography.headlineSmall)
        Text("Broader web images", style = MaterialTheme.typography.titleMedium)
        Text("Connect your HTTPS search gateway. The Brave provider key belongs only on your server, never in this app.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS gateway URL") },
            placeholder = { Text("https://search.your-domain.example") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("App access token") },
            supportingText = { Text(if (state.gatewayConfigured) "Leave blank to keep the token for this server" else "Use the separate token configured on your gateway") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(url, token) }, enabled = !state.savingSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (state.savingSettings) "Saving securely…" else "Save connection")
        }
        Text(if (state.gatewayConfigured) "Connection saved on this device. Run a search to verify the server."
            else "Web search is not configured. Open catalogs can still be used.", style = MaterialTheme.typography.bodySmall)
        Text("Brave returns up to 200 images per query. Browsing that result batch does not repeat the provider request. SafeSearch is strict.",
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Catalog sources", style = MaterialTheme.typography.titleMedium)
        Text("Used only in Open catalogs mode. Source changes apply to your next search.", style = MaterialTheme.typography.bodySmall)
        SourceProvider.entries.filter { it != SourceProvider.BRAVE }.forEach { provider ->
            val result = state.states.find { it.provider == provider }
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.label, style = MaterialTheme.typography.titleSmall)
                        Text(result?.error ?: result?.let { "${it.items.size} returned on the last page" }
                            ?: if (provider.requiresKey) "Your own API key required; source quota applies" else "Public catalog API",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = provider in state.providers, onCheckedChange = { onProvider(provider, it) })
                }
                if (provider.requiresKey) {
                    var key by remember(provider) { mutableStateOf("") }
                    OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                        label = { Text("New ${provider.label} key") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { onKey(provider, key); key = "" }) { Text("Store key (blank removes it)") }
                }
            }
        }
        Text("Web results are not automatically licensed for reuse. Check the original source before downloading or sharing.",
            style = MaterialTheme.typography.bodySmall)
    }
}
