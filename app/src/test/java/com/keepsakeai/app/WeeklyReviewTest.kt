package com.keepsakeai.app

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.review.computeWeek
import com.keepsakeai.app.ui.spend.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {
    private val now = 1_795_000_000_000L
    private fun e(m: String, amt: Double, daysAgo: Int, cat: String) =
        ExpenseEntity(merchant = m, amount = amt, category = cat, sourceMethod = "UPI · ICICI Bank", timestamp = now - daysAgo * DAY)
    private fun mem(t: String, daysAgo: Int) = MemoryEntity(title = t, rawContent = t, aiSummary = t, collectionName = "Travel",
        source = CaptureSource.TEXT_NOTE, createdAt = now - daysAgo * DAY)

    @Test
    fun comparesWithPreviousWeekAndFindsTheMover() {
        val data = listOf(
            e("Swiggy", 1200.0, 1, "Food"), e("Zomato", 800.0, 3, "Food"), e("Uber", 300.0, 2, "Transport"),
            e("Swiggy", 500.0, 9, "Food"), e("Uber", 400.0, 10, "Transport"),
        )
        val w = computeWeek(data, listOf(mem("Goa plan", 2), mem("Old", 20)), now)
        assertEquals(2300.0, w.spent, 0.01)
        assertEquals(900.0, w.prevSpent, 0.01)
        assertEquals(155, w.change)
        assertEquals("Food", w.topCategory!!.first)
        assertEquals("Food", w.biggestMover!!.first)
        assertTrue(w.insight.startsWith("Food jumped"))
        assertEquals(1, w.kept)
        assertEquals(listOf("Goa plan"), w.highlights)
    }

    @Test
    fun quietWeek() {
        val w = computeWeek(emptyList(), emptyList(), now)
        assertTrue(w.insight.startsWith("A quiet week"))
    }
}
