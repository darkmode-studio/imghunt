package com.sandeep.imghunt

import com.sandeep.imghunt.engine.AspectFilter
import com.sandeep.imghunt.engine.FormatFilter
import com.sandeep.imghunt.engine.LicenseFilter
import com.sandeep.imghunt.engine.PhotoItem
import com.sandeep.imghunt.engine.PhotoSources
import com.sandeep.imghunt.engine.ResolutionFilter
import com.sandeep.imghunt.engine.SearchFilters
import com.sandeep.imghunt.engine.SourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgHuntFiltersTest {
    @Test
    fun resolutionAndAspectFilteringWorks() {
        val sample = PhotoItem(
            id = "1",
            title = "4K Landscape",
            previewUrl = "https://images.nasa.gov/thumb.jpg",
            highResUrl = "https://images.nasa.gov/full.jpg",
            sourcePageUrl = "https://images.nasa.gov/details/1",
            provider = SourceProvider.NASA,
            width = 4000,
            height = 2500,
            mimeType = "image/jpeg",
            isOpenLicense = true,
            creator = "NASA"
        )
        val filters = SearchFilters(
            resolution = ResolutionFilter.UHD,
            aspect = AspectFilter.LANDSCAPE,
            format = FormatFilter.JPG,
            license = LicenseFilter.OPEN
        )
        assertTrue(filters.matches(sample))
        // Narrow banner (3840x200) must fail 4K short-side check (min 2160)
        assertFalse(filters.matches(sample.copy(width = 3840, height = 200)))
        // Missing dimensions must fail strict resolution/aspect filters
        assertFalse(filters.matches(sample.copy(width = 0, height = 0)))
        assertEquals("jpg", sample.inferredExtension())
        assertTrue(sample.attributionText().contains("NASA"))
    }

    @Test
    fun magicBytesAndQueryVariantsWork() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals("image/png", PhotoSources.sniffMimeFromBytes(pngHeader))
        val variants = PhotoSources.queryVariants("namitha kapoor")
        assertTrue(variants.contains("namitha kapoor"))
        assertTrue(variants.size >= 2)
    }
}
