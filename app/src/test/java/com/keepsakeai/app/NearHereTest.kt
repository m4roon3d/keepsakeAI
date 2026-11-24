package com.keepsakeai.app

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.places.NearHere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearHereTest {
    private fun m(id: Long, lat: Double?, lng: Double?) = MemoryEntity(
        id = id, title = "t$id", rawContent = "", aiSummary = "", collectionName = "c",
        source = CaptureSource.TEXT_NOTE, lat = lat, lng = lng
    )

    @Test
    fun picksOnlyWithinRadiusNearestFirst() {
        val here = 12.9716 to 77.5946
        val list = listOf(
            m(1, 12.9726, 77.5946),   // ~110 m
            m(2, 12.9719, 77.5946),   // ~33 m
            m(3, 12.9916, 77.5946),   // ~2.2 km
            m(4, null, null)
        )
        assertEquals(listOf(2L, 1L), NearHere.nearby(list, here.first, here.second).map { it.memory.id })
    }

    @Test
    fun readsCoordinatesFromSavedText() {
        val p = NearHere.parseCoords("Taken: Sat 1 Nov 2026, 1:00 PM\nLocation: 12.97160, 77.59460 (near Indiranagar)")
        assertEquals(12.9716, p!!.first, 1e-6)
        assertEquals(77.5946, p.second, 1e-6)
        assertNull(NearHere.parseCoords("no place here"))
        assertNull(NearHere.parseCoords("Location: 123.0, 77.0"))
    }
}
