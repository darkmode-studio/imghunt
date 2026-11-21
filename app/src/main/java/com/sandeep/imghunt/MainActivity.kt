package com.sandeep.imghunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import com.sandeep.imghunt.ui.ImgHuntTheme
import com.sandeep.imghunt.ui.NativeImgHuntApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImgHuntTheme {
                // Insets are measured by Android; never derive layout dp from physical display pixels.
                Box(Modifier.fillMaxSize().imePadding()) { NativeImgHuntApp() }
            }
        }
    }
}
