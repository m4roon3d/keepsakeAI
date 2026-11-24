package com.keepsakeai.app

import com.keepsakeai.app.ai.PlaceMarkers
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceMarkersTest {
    @Test
    fun replacesMarkersWithNamesAndCollectsPlaces() {
        val p = PlaceMarkers.parse("Day 1: breakfast at [[place: Vidyarthi Bhavan, Basavanagudi, Bengaluru]], then [[place: Lalbagh, Mavalli, Bengaluru]].")
        assertEquals("Day 1: breakfast at Vidyarthi Bhavan, then Lalbagh.", p.text)
        assertEquals(listOf("Vidyarthi Bhavan", "Lalbagh"), p.places.map { it.name })
        assertEquals("Lalbagh, Mavalli, Bengaluru", p.places[1].query)
    }

    @Test
    fun repeatedPlacesAreListedOnce() {
        val p = PlaceMarkers.parse("[[place: Lalbagh, Mavalli, Bengaluru]] again [[place: lalbagh, mavalli, bengaluru]]")
        assertEquals(1, p.places.size)
    }

    @Test
    fun textWithoutMarkersIsUntouched() {
        val p = PlaceMarkers.parse("Take [2] with you. Nothing to tag.")
        assertEquals("Take [2] with you. Nothing to tag.", p.text)
        assertEquals(0, p.places.size)
    }
}
