package com.sandeep.imghunt.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.*
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import com.sandeep.imghunt.engine.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(vm: BrowserViewModel, loader: ImageLoader, favorites: List<PhotoItem>,
    onOpen: (PhotoItem) -> Unit, onFavorite: (PhotoItem) -> Unit, onSave: (PhotoItem) -> Unit,
    initialUrl: String = vm.initialUrl, onWebViewReady: (WebView) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current; val keyboard=LocalSoftwareKeyboardController.current
    val scope=rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var epoch by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var clearCookies by remember { mutableStateOf(false) }
    var confirmPreviews by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) vm.importSelected() else vm.message("Storage permission is needed to import on this Android version")
    }
    val startImport: () -> Unit = {
        if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.importSelected()
    }
    val go: () -> Unit = { runCatching { BrowserImagePolicy.address(state.query) }.onSuccess { keyboard?.hide();web?.loadUrl(it) }
        .onFailure { vm.message(it.message ?: "Enter a search or HTTPS address") } }
    LaunchedEffect(state.showImages,web) { runCatching {
        if(state.showImages) {web?.onPause();web?.pauseTimers()} else {web?.onResume();web?.resumeTimers()}
    } }
    BackHandler(enabled=state.showImages || state.canBack) {
        if(state.showImages) vm.results(false) else web?.goBack()
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(state.query,vm::query,singleLine=true,label={Text("Search images or enter website address")},
                leadingIcon={IconButton(onClick={web?.loadUrl("https://www.google.com/imghp")}) {Icon(Icons.Outlined.Home,"Browser home")}},
                trailingIcon={IconButton(onClick=go){Icon(Icons.Outlined.Search,"Search browser")}},
                modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={go()}))
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={web?.goBack()},enabled=state.canBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Browser back")}
                IconButton(onClick={web?.goForward()},enabled=state.canForward){Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Browser forward")}
                IconButton(onClick={if(web==null)epoch++ else web?.reload()}){Icon(Icons.Outlined.Refresh,"Reload browser")}
                FilledTonalButton(onClick={ val page=web?.url ?: state.pageUrl
                    web?.evaluateJavascript(BrowserImagePolicy.collectScript){vm.collected(it,page)}
                },enabled=web!=null&&!state.collecting,modifier=Modifier.weight(1f)){Text("Find Images")}
                Box { IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"Browser options")}
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text("Previous detected images (${state.items.size})")},onClick={menu=false;vm.results(true)})
                        DropdownMenuItem(text={Text("Login and website support")},onClick={menu=false;info=true})
                        DropdownMenuItem(text={Text("Open in system browser")},onClick={menu=false
                            BrowserImagePolicy.https(state.pageUrl)?.let { url -> runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure {vm.message("No browser available")} }
                        })
                        DropdownMenuItem(text={Text("Clear browser site data")},onClick={menu=false;clearCookies=true})
                    }
                }
            }
            Text(state.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(horizontal=12.dp))
            Text(runCatching { Uri.parse(state.pageUrl).host }.getOrNull().orEmpty(),maxLines=1,
                style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=12.dp,vertical=2.dp))
            if(state.progress<100) LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth())
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp)) }
            key(epoch) { BrowserPane(vm,Modifier.weight(1f).fillMaxWidth(),initialUrl){web=it;if(it!=null)onWebViewReady(it)} }
        }
        if(state.showImages) BrowserImageSelectionScreen(state,loader,{vm.results(false)},vm::toggle,vm::selectAll,vm::choose,onOpen,onFavorite,
            onImport={if(state.items.any {it.photo.id in state.selected && it.quality=="Preview only"})confirmPreviews=true else startImport()},
            onSource={url -> BrowserImagePolicy.https(url)?.let {vm.results(false);web?.loadUrl(it)} })
    }
    state.pending?.let { AlertDialog(onDismissRequest=vm::dismissSelection,title={Text("Inspect selected image?")},
        text={Text("Review available resolution options before importing. A displayed thumbnail is not necessarily the original.")},
        confirmButton={TextButton(onClick=vm::addSelected){Text("Inspect image")}},dismissButton={TextButton(onClick=vm::dismissSelection){Text("Cancel")}}) }
    if(confirmPreviews) AlertDialog(onDismissRequest={confirmPreviews=false},title={Text("Selection includes preview-only images")},
        text={Text("These will remain thumbnails, not full-resolution originals. Open their source pages to look for larger images, or explicitly import the previews.")},
        confirmButton={TextButton(onClick={confirmPreviews=false;startImport()}){Text("Import previews too")}},
        dismissButton={TextButton(onClick={confirmPreviews=false}){Text("Review selection")}})
    if(info) AlertDialog(onDismissRequest={info=false},title={Text("Website sessions")},
        text={Text("Website cookies stay inside ImgHunt's browser. Supported login sessions are preserved according to website expiry rules. Google and other providers may block embedded sign-in; use the system browser when required. Browser cookies are not copied to image download jobs, so some protected images cannot be imported.")},
        confirmButton={TextButton(onClick={info=false}){Text("Understood")}})
    if(clearCookies) AlertDialog(onDismissRequest={clearCookies=false},title={Text("Clear ImgHunt website sessions?")},
        text={Text("This signs you out of websites in ImgHunt only. It does not clear Chrome or delete imported images.")},
        confirmButton={TextButton(onClick={clearCookies=false;CookieManager.getInstance().removeAllCookies {scope.launch(Dispatchers.IO){CookieManager.getInstance().flush()}}
            WebStorage.getInstance().deleteAllData();web?.reload()}){Text("Clear site data")}},
        dismissButton={TextButton(onClick={clearCookies=false}){Text("Cancel")}})
}
