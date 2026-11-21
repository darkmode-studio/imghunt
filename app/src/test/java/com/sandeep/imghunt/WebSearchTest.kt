package com.sandeep.imghunt

import com.sandeep.imghunt.data.GatewayAddress
import com.sandeep.imghunt.data.GatewayConfig
import com.sandeep.imghunt.engine.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WebSearchTest {
    private fun photo(i: Int) = PhotoItem("web_$i", "Image $i", "https://cdn.example.com/$i.jpg",
        "https://cdn.example.com/$i.jpg", "https://example.com/page", SourceProvider.BRAVE)

    @Test fun parsesOriginalImageSeparatelyFromSourceAndDoesNotInventRights() {
        val batch = WebImageClient.parse("""{"items":[{"title":"Mountain","imageUrl":"https://cdn.example.com/full.jpg",
            "thumbnailUrl":"https://cdn.example.com/small.jpg","sourcePageUrl":"https://example.com/article",
            "isOpenLicense":true,"width":2400,"height":1600}],"correctedQuery":"mountains"}""")
        val item = batch.items.single()
        assertEquals("https://cdn.example.com/full.jpg", item.highResUrl)
        assertEquals("https://example.com/article", item.sourcePageUrl)
        assertEquals("example.com", item.sourceLabel)
        assertFalse(item.isOpenLicense)
        assertFalse(SearchFilters(license = LicenseFilter.OPEN).matches(item))
        assertEquals("mountains", batch.correctedQuery)
    }
    @Test(expected = IOException::class) fun malformedResponseIsNotAnEmptySuccess() {
        WebImageClient.parse("""{"unexpected":[]}""")
    }
    @Test fun dropsUnsafeImagesAndDuplicates() {
        val item = """{"imageUrl":"https://cdn.example.com/a.jpg","sourcePageUrl":"https://example.com/a"}"""
        val unsafe = """{"imageUrl":"file:///etc/passwd","sourcePageUrl":"https://example.com/a"}"""
        assertEquals(1, WebImageClient.parse("""{"items":[$item,$item,$unsafe]}""").items.size)
    }
    @Test fun resultWindowHasRealEndAndStableDeduplication() {
        val window = ResultWindow((0 until 50).map(::photo) + photo(0), 24)
        assertEquals(24, window.revealNext().size)
        assertTrue(window.hasMore)
        assertEquals(48, window.revealNext().size)
        assertEquals(50, window.revealNext().size)
        assertFalse(window.hasMore)
        assertEquals(50, window.revealNext().size)
    }
    @Test fun emptyParsedCatalogPageCanStillContinue() {
        val page = OpenCatalog.parse(SourceProvider.WIKIMEDIA,
            JSONObject("""{"query":{"pages":{}},"continue":{"gsroffset":36}}"""), 1)
        assertTrue(page.items.isEmpty())
        assertTrue(page.hasMore)
    }
    @Test fun gatewayAddressAndCredentialRedaction() {
        assertEquals("https://search.example.com", GatewayAddress.normalize("https://search.example.com/"))
        for (url in listOf("http://search.example.com", "https://user:pass@search.example.com", "https://search.example.com/?key=secret")) {
            assertTrue(runCatching { GatewayAddress.normalize(url) }.isFailure)
        }
        val secret = "never-print-this-secret-token"
        assertFalse(GatewayConfig("https://search.example.com", secret).toString().contains(secret))
    }
    @Test fun unknownImageDimensionsDoNotPretendToBe4k() {
        assertFalse(SearchFilters(resolution = ResolutionFilter.UHD).matches(photo(1)))
        assertTrue(SearchFilters().matches(photo(1)))
    }
}
