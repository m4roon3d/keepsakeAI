package com.keepsakeai.app

import com.keepsakeai.app.data.local.PlaceVisitEntity
import com.keepsakeai.app.places.PlacesPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesPromptTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private fun v(daysAgo: Int, name: String?, stay: Long = 30 * min) =
        PlaceVisitEntity(lat = 12.9716, lng = 77.5946, arrivedAt = now - daysAgo * 86_400_000L, lastSeenAt = now - daysAgo * 86_400_000L + stay, name = name)

    @Test
    fun listsNamesNeverCoordinates() {
        val out = PlacesPrompt.block(listOf(v(1, "Indiranagar, Bengaluru")), true, now)
        assertTrue("Indiranagar, Bengaluru" in out)
        assertFalse("12.97" in out || "77.59" in out)
    }

    @Test
    fun skipsOldAndShortStays() {
        val out = PlacesPrompt.block(listOf(v(20, "Old place"), v(1, "Blip", stay = 5 * min)), true, now)
        assertFalse("Old place" in out || "Blip" in out)
        assertTrue("nothing recorded" in out)
    }

    @Test
    fun saysWhenOff() {
        assertTrue("not switched on" in PlacesPrompt.block(listOf(v(1, "X")), false, now))
    }
}
