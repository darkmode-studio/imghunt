package com.sandeep.imghunt

import com.sandeep.imghunt.browser.*
import com.sandeep.imghunt.engine.SearchMode
import com.sandeep.imghunt.ui.NativeSearchState
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class BrowserImagePolicyTest {
    @Test fun preservesSignedImageUrlEscaping() {
        val url = "https://cdn.example.com/a%20b.jpg?token=x%2By%2Fz"
        assertEquals(url, BrowserImagePolicy.https(url + "#preview"))
    }
    @Test fun rejectsPrivateAndUnsafeUrls() {
        for (url in listOf("http://example.com/a.jpg", "file:///etc/passwd", "https://localhost/a", "https://127.0.0.1/a", "https://192.168.1.1/a", "https://100.64.1.1/a", "https://[::1]/a", "https://user:password@example.com/a")) {
            assertNull(url, BrowserImagePolicy.https(url))
        }
    }
    @Test fun distinguishesOriginalFromGoogleThumbnail() {
        val full = "https://cdn.example.com/photo.jpg?token=a%2Bb"
        val source = "https://example.com/article"
        val anchor = "https://www.google.com/imgres?imgurl=" + URLEncoder.encode(full, "UTF-8") + "&imgrefurl=" + URLEncoder.encode(source, "UTF-8")
        val image = BrowserCandidate("https://encrypted-tbn0.gstatic.com/thumbnail", "https://www.google.com/search?q=mountains", anchor = anchor)
        assertEquals(full, BrowserImagePolicy.imageUrl(image))
        assertEquals(source, BrowserImagePolicy.sourceUrl(image))
    }
    @Test fun freeCatalogModeIsTheDefault() { assertEquals(SearchMode.CATALOGS, NativeSearchState().mode) }
    @Test fun searchTextIsEncodedNotExecuted() {
        assertTrue(BrowserImagePolicy.address("mountains & lakes").contains("mountains+%26+lakes"))
        assertFalse(BrowserImagePolicy.embedded("data:image/svg+xml;base64,PHN2Zz4="))
    }
}
