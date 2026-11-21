package com.sandeep.imghunt.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sandeep.imghunt.data.DownloadRecord
import java.io.File

fun openNativeDownloadedImage(context: Context, record: DownloadRecord) {
    val saved = Uri.parse(requireNotNull(record.savedUri))
    val uri = if (saved.scheme == "file") FileProvider.getUriForFile(context,
        "${context.packageName}.files", File(requireNotNull(saved.path))) else saved
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, record.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
