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
            MaterialTheme {
                NativeSearchScreen(NativeSearchState(query = "mountains"), loader, rememberLazyGridState(),
                    onQuery = {}, onSearch = onSearch, onCancel = {}, onMode = {}, onSettings = onSettings,
                    onMore = {}, onOpen = {}, onFavorite = {}, onDownload = {})
            }
        }
    }
    @Test fun missingGatewayHasAnActionableSetupState() {
        var opened = false
        render(onSettings = { opened = true })
        rule.onNodeWithText("Connect broader web search").assertIsDisplayed()
        rule.onNodeWithText("Open Search settings").performClick()
        rule.runOnIdle { assertTrue(opened) }
        rule.onNodeWithText("No images matched. Try another term or relax your filters.").assertDoesNotExist()
    }
    @Test fun providerSearchIsAnExplicitUserAction() {
        var searches = 0
        render(onSearch = { searches++ })
        rule.runOnIdle { assertEquals(0, searches) }
        rule.onNodeWithContentDescription("Search images").performClick()
        rule.runOnIdle { assertEquals(1, searches) }
    }
}
