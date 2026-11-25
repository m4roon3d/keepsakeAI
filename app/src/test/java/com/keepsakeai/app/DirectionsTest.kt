package com.keepsakeai.app

import com.keepsakeai.app.maps.Directions
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsTest {
    @Test
    fun namesAreEncodedWithPercentTwentyForSpaces() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Cubbon%20Park%2C%20Bengaluru",
            Directions.url("Cubbon Park, Bengaluru")
        )
    }

    @Test
    fun coordinatesPassThrough() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=12.9716%2C77.5946", Directions.url(12.9716, 77.5946))
    }

    @Test
    fun surroundingSpacesAreTrimmedAndSpecialCharactersEscaped() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=Brahmin%27s%20%26%20Co", Directions.url("  Brahmin's & Co "))
    }

    @Test
    fun placeLinkShowsAPinNotARoute() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=12.9716,77.5946", Directions.placeUrl(12.9716, 77.5946))
    }
}
