package com.keepsakeai.app

import com.keepsakeai.app.places.OsmPlaces
import com.keepsakeai.app.places.PlaceTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmPlacesTest {
    private val reply = """{"elements":[
        {"type":"node","lat":12.90010,"lon":77.58510,"tags":{"name":"Far Cafe","amenity":"cafe"}},
        {"type":"node","lat":12.90001,"lon":77.58501,"tags":{"name":"Inya","amenity":"cafe"}},
        {"type":"node","lat":12.90002,"lon":77.58502,"tags":{"name":"Parking","amenity":"parking"}},
        {"type":"way","center":{"lat":12.90003,"lon":77.58503},"tags":{"name":"Inya","shop":"coffee"}},
        {"type":"node","lat":12.9,"lon":77.585,"tags":{"amenity":"cafe"}}
    ]}"""

    @Test fun nearestNamedFirstAndNoJunk() {
        val v = OsmPlaces.parse(reply, 12.9, 77.585)
        assertEquals(listOf("Inya", "Far Cafe"), v.map { it.name })
        assertEquals("cafe", v.first().kind)
        assertTrue(v.first().meters < 5)
    }

    private val mixed = """{"elements":[
        {"type":"way","center":{"lat":12.90001,"lon":77.58501},"tags":{"name":"Sri Residency Block","building":"apartments"}},
        {"type":"node","lat":12.90020,"lon":77.58520,"tags":{"name":"Meghana Foods","amenity":"restaurant"}},
        {"type":"node","lat":12.90030,"lon":77.58530,"tags":{"name":"City Hospital","healthcare":"hospital"}},
        {"type":"node","lat":12.90040,"lon":77.58540,"tags":{"name":"Old Gate","historic":"monument"}},
        {"type":"way","center":{"lat":12.90002,"lon":77.58502},"tags":{"name":"Clock Tower","man_made":"tower"}},
        {"type":"way","center":{"lat":12.90003,"lon":77.58503},"tags":{"name":"House 12","building":"yes"}}
    ]}"""

    @Test fun businessesAndLandmarksAllCountAndBusinessesRankFirst() {
        val v = OsmPlaces.parse(mixed, 12.9, 77.585)
        assertEquals(listOf("Meghana Foods", "City Hospital", "Old Gate", "Sri Residency Block", "Clock Tower", "House 12"), v.map { it.name })
        assertEquals(listOf("restaurant", "hospital", "monument", "apartments", "tower", "building"), v.map { it.kind })
        assertEquals(listOf(false, false, false, true, true, true), v.map { it.landmark })
    }

    @Test fun queryAsksForEveryPlaceKind() {
        val q = OsmPlaces.query(12.9, 77.585, 75)
        listOf("amenity", "shop", "historic", "healthcare", "building", "man_made", "railway").forEach { assertTrue(it, q.contains("[\"$it\"]")) }
        assertTrue(q.contains("around:75,"))
    }

    @Test fun badJsonIsEmpty() = assertTrue(OsmPlaces.parse("oops", 0.0, 0.0).isEmpty())

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
}
