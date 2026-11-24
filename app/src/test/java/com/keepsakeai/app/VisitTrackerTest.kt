package com.keepsakeai.app

import com.keepsakeai.app.data.local.PlaceVisitEntity
import com.keepsakeai.app.places.VisitTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTrackerTest {
    private val t0 = 1_000_000_000L
    private val min = 60_000L
    private fun open(at: Long = t0, seen: Long = t0) = PlaceVisitEntity(id = 1, lat = 12.9716, lng = 77.5946, arrivedAt = at, lastSeenAt = seen)

    @Test
    fun firstReadingStartsAStay() {
        assertTrue(VisitTracker.step(null, 12.97, 77.59, t0) is VisitTracker.Step.Start)
    }

    @Test
    fun staysWithin100mExtendTheStay() {
        // ~50 m north
        val s = VisitTracker.step(open(), 12.97205, 77.5946, t0 + 15 * min)
        assertTrue(s is VisitTracker.Step.Extend)
        assertEquals(t0 + 15 * min, (s as VisitTracker.Step.Extend).visit.lastSeenAt)
    }

    @Test
    fun movingAwayStartsANewStayAndClosesTheOldOne() {
        // ~1.1 km north
        val s = VisitTracker.step(open(), 12.9816, 77.5946, t0 + 15 * min)
        assertTrue(s is VisitTracker.Step.Start)
        assertEquals(1L, (s as VisitTracker.Step.Start).closing?.id)
    }

    @Test
    fun aLongGapIsNotStitchedIntoOneStay() {
        val s = VisitTracker.step(open(), 12.9716, 77.5946, t0 + 3 * 60 * min)
        assertTrue(s is VisitTracker.Step.Start)
    }

    @Test
    fun tenMinutesMakesAVisit() {
        assertFalse(VisitTracker.isVisit(open(seen = t0 + 9 * min)))
        assertTrue(VisitTracker.isVisit(open(seen = t0 + 10 * min)))
    }

    @Test
    fun distanceIsRoughlyRight() {
        val d = VisitTracker.distanceMeters(12.9716, 77.5946, 12.9816, 77.5946)
        assertTrue(d in 1_050.0..1_170.0)
    }
}
