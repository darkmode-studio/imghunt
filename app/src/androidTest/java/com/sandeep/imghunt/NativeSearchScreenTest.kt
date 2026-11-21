package com.sandeep.imghunt

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.ui.NativeSearchScreen
import com.sandeep.imghunt.ui.NativeSearchState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeSearchScreenTest {
    @get:Rule val rule = createComposeRule()
    private fun render(onSearch: () -> Unit = {}, onSettings: () -> Unit = {}) {
        rule.setContent {
            val context = LocalContext.current
            val loader = remember { PhotoSources.createImageLoader(context) }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            MaterialTheme { NativeSearchScreen(NativeSearchState(query = "mountains"), loader, rememberLazyGridState(),
                {}, onSearch, {}, {}, onSettings, {}, {}, {}, {}) }
        }
    }
    @Test fun freeScreenDoesNotRequireGatewayCredentials() {
        var opened = false
        render(onSettings = { opened = true })
        rule.onNodeWithText("Free catalog search").assertIsDisplayed()
        rule.onNodeWithText("HTTPS gateway URL").assertDoesNotExist()
        rule.onNodeWithText("App access token").assertDoesNotExist()
        rule.onNodeWithText("Free sources").performClick()
        rule.runOnIdle { assertTrue(opened) }
    }
    @Test fun searchRequiresAnExplicitUserAction() {
        var count = 0
        render(onSearch = { count++ })
        rule.runOnIdle { assertEquals(0, count) }
        rule.onNodeWithContentDescription("Search images").performClick()
        rule.runOnIdle { assertEquals(1, count) }
    }
}
