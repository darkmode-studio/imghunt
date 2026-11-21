package com.sandeep.imghunt

import com.sandeep.imghunt.browser.*
import org.junit.Assert.*
import org.junit.Test

class BrowserResolutionTest {
    @Test fun highestResponsiveCandidateIsPreferredWithoutEnlargingThumbnail(){
        val image=BrowserCandidate("https://cdn.example/thumb.jpg","https://site.example/page",options=listOf(
            BrowserImageOption("https://cdn.example/medium.jpg",640,0,"Responsive image"),
            BrowserImageOption("https://cdn.example/full.jpg",2048,0,"Responsive image")))
        assertEquals("https://cdn.example/full.jpg",BrowserImagePolicy.imageUrl(image))
    }
    @Test fun unsafeOriginalHintsAreNotUsed(){
        val image=BrowserCandidate("https://cdn.example/thumb.jpg","https://site.example/page",options=listOf(
            BrowserImageOption("file:///etc/passwd",9000,0,"Original link"),
            BrowserImageOption("https://127.0.0.1/private",9000,0,"Original link")))
        assertEquals("https://cdn.example/thumb.jpg",BrowserImagePolicy.imageUrl(image))
    }
    @Test fun websiteAddressesAndSearchTermsAreDistinct(){
        assertEquals("https://example.com/photos",BrowserImagePolicy.address("example.com/photos"))
        assertTrue(BrowserImagePolicy.address("mountains and lakes").startsWith("https://www.google.com/search?"))
    }
}
