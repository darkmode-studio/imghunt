package com.sandeep.imghunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sandeep.imghunt.ui.ImgHuntTheme
import com.sandeep.imghunt.ui.NativeImgHuntApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ImgHuntTheme { NativeImgHuntApp() } }
    }
}
