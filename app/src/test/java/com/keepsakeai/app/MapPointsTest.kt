package com.keepsakeai.app

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PlaceVisitEntity
import com.keepsakeai.app.places.MapPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapPointsTest {
    private fun m(id: Long, lat: Double?, lng: Double?, raw: String = "") = MemoryEntity(
        id = id, title = "t$id", rawContent = raw, aiSummary = "", collectionName = "Travel",
        source = CaptureSource.TEXT_NOTE, lat = lat, lng = lng
    )

    @Test
    fun memoriesUseColumnsThenSavedText() {
        val pins = MapPoints.memories(listOf(m(1, 12.9, 77.5), m(2, null, null, "Location: 13.00000, 77.60000"), m(3, null, null)))
        assertEquals(listOf("m1", "m2"), pins.map { it.key })
        assertEquals(13.0, pins[1].lat, 1e-6)
    }

    @Test
    fun onlyRealStaysBecomePins() {
        val min = 60_000L
        val pins = MapPoints.stays(listOf(
            PlaceVisitEntity(id = 1, lat = 1.0, lng = 2.0, arrivedAt = 0, lastSeenAt = 30 * min, name = "Cafe"),
            PlaceVisitEntity(id = 2, lat = 1.0, lng = 2.0, arrivedAt = 0, lastSeenAt = 5 * min)
        ))
        assertEquals(1, pins.size)
        assertEquals("stayed 30m", pins[0].subtitle)
    }

    @Test
    fun boundsCoverAllPins() {
        val pins = MapPoints.memories(listOf(m(1, 10.0, 70.0), m(2, 20.0, 80.0)))
        assertEquals(listOf(10.0, 70.0, 20.0, 80.0), MapPoints.bounds(pins)!!.toList())
        assertNull(MapPoints.bounds(emptyList()))
    }
}
