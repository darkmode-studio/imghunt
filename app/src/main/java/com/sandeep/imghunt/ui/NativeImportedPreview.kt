package com.sandeep.imghunt.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.sandeep.imghunt.engine.PhotoItem
import java.io.File

@Composable
fun NativeImportedPreview(photo: PhotoItem, loader: ImageLoader, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    val context=LocalContext.current
    var scale by remember(photo.id){mutableFloatStateOf(1f)}
    var x by remember(photo.id){mutableFloatStateOf(0f)}
    var y by remember(photo.id){mutableFloatStateOf(0f)}
    var error by remember(photo.id){mutableStateOf<String?>(null)}
    Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}
            Text(photo.title,modifier=Modifier.weight(1f).padding(12.dp),maxLines=2)
            IconButton(onClick=onFavorite){Icon(if(favorite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Toggle bookmark")}
            IconButton(onClick={runCatching {
                val saved=Uri.parse(photo.highResUrl)
                val uri=if(saved.scheme=="file")FileProvider.getUriForFile(context,"${context.packageName}.files",File(requireNotNull(saved.path))) else saved
                val intent=Intent(Intent.ACTION_SEND).setType(photo.mimeType ?: "image/*").putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=ClipData.newRawUri("Imported image",uri) }
                context.startActivity(Intent.createChooser(intent,"Share image"))
            }.onFailure {error="The saved file could not be shared"}}){Icon(Icons.Outlined.Share,"Share imported image")}
        }
        AsyncImage(model=photo.highResUrl,imageLoader=loader,contentDescription=photo.title,contentScale=ContentScale.Fit,
            modifier=Modifier.weight(1f).fillMaxWidth().pointerInput(photo.id){detectTransformGestures { _,pan,zoom,_ ->
                scale=(scale*zoom).coerceIn(1f,5f);x=if(scale>1) x+pan.x else 0f;y=if(scale>1)y+pan.y else 0f
            }}.graphicsLayer(scaleX=scale,scaleY=scale,translationX=x,translationY=y),
            onError={error="This local file is unavailable. It may have been moved or deleted outside ImgHunt."})
        Text("Saved locally · ${photo.width} × ${photo.height} · ${photo.mimeType.orEmpty()}",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
        error?.let {Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
    } }
}
