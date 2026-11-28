package com.keepsakeai.app

import com.keepsakeai.app.places.PlaceTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTagTest {
    @Test fun setsPlaceOnLocationLine() {
        val raw = "Taken: Sat\nLocation: 12.90001, 77.58501 (near 8th Main Rd, JP Nagar)\nmore"
        assertEquals("8th Main Rd, JP Nagar", PlaceTag.placeOf(raw))
        val out = PlaceTag.withPlace(raw, "Inya", null, null)
        assertEquals("Taken: Sat\nLocation: 12.90001, 77.58501 (near Inya)\nmore", out)
    }

    @Test fun addsLocationOrPlaceLine() {
        assertEquals("Location: 12.90000, 77.58500 (near Inya)\nnotes", PlaceTag.withPlace("notes", "Inya", 12.9, 77.585))
        assertEquals("Place: Inya\nnotes", PlaceTag.withPlace("notes", "Inya", null, null))
        assertEquals("Place: Inya 2\nnotes", PlaceTag.withPlace("Place: Inya\nnotes", "Inya 2", null, null))
        assertEquals("Inya 2", PlaceTag.plainPlace("Place: Inya 2\nnotes"))
        assertNull(PlaceTag.placeOf("no location here"))
    }

    @Test fun shortPlaceKeepsTheFirstTwoParts() {
        val raw = "note\n\nLocation: 12.90000, 77.58000 (near Linto Coffee, 24th Main Road, J. P. Nagar, Bengaluru)"
        org.junit.Assert.assertEquals("Linto Coffee, 24th Main Road", com.keepsakeai.app.places.PlaceTag.shortPlace(raw))
        org.junit.Assert.assertNull(com.keepsakeai.app.places.PlaceTag.shortPlace("just a note"))
        org.junit.Assert.assertEquals("Lalbagh", com.keepsakeai.app.places.PlaceTag.shortPlace("Place: Lalbagh"))
    }
}
