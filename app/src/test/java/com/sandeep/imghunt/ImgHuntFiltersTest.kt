package com.sandeep.imghunt

import com.sandeep.imghunt.engine.AspectFilter
import com.sandeep.imghunt.engine.FormatFilter
import com.sandeep.imghunt.engine.LicenseFilter
import com.sandeep.imghunt.engine.PhotoItem
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
            isOpenLicense = true
        )
        val filters = SearchFilters(
            resolution = ResolutionFilter.UHD,
            aspect = AspectFilter.LANDSCAPE,
            format = FormatFilter.JPG,
            license = LicenseFilter.OPEN
        )
        assertTrue(filters.matches(sample))
        assertFalse(filters.matches(sample.copy(width = 1000, height = 1500)))
        assertEquals("jpg", sample.inferredExtension())
    }
}
