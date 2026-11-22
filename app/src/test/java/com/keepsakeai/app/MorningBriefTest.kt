package com.keepsakeai.app

import com.keepsakeai.app.calendar.CalEvent
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.Todo
import com.keepsakeai.app.ui.brief.buildBrief
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefTest {
    private val now = startOfDay(1_795_000_000_000L) + 8 * 60 * 60 * 1000 // 8am

    private fun mem(id: Long, title: String, daysAgo: Int, collection: String, url: String? = null) = MemoryEntity(
        id = id, title = title, rawContent = title, aiSummary = title, collectionName = collection,
        source = CaptureSource.SHARE_SHEET, sourceUrl = url, createdAt = now - daysAgo * DAY
    )

    @Test
    fun gathersTodayMoneyAndRevisit() {
        val reminders = listOf(
            KeepReminder(1, "Harris Jayaraj concert", startOfDay(now) + DAY + 19 * 3600_000L, "event", 5, hasTime = true),
            KeepReminder(2, "Old thing", now - 3 * DAY, "event", 0)
        )
        val todos = listOf(
            Todo(10, "Pay BESCOM", due = now - DAY), Todo(11, "Book Goa hotel", due = now + 3600_000L), Todo(12, "Someday", due = null)
        )
        val memories = listOf(
            mem(20, "Spark tuning article", 5, "Study Notes", "https://x.y"),
            mem(21, "Opened already", 6, "Watch Later", "https://z"),
            mem(22, "Fresh link", 1, "Watch Later", "https://q")
        )
        val b = buildBrief(reminders, todos, memories, emptyList(), pendingUpi = 2, budgets = emptyMap(), viewed = setOf(21L), now = now)
        assertEquals("Harris Jayaraj concert", b.today.first().text)
        assertTrue(b.today.first().sub!!.startsWith("tomorrow · 7:00 pm"))
        assertTrue(b.today.any { it.text == "1 overdue to-do" && it.urgent })
        assertTrue(b.today.any { it.text == "Book Goa hotel" })
        assertEquals("2 payments to review", b.money.first().text)
        assertEquals(listOf("Spark tuning article"), b.revisit.map { it.text })
        assertTrue(b.summary().contains("Harris Jayaraj concert"))
    }

    @Test
    fun calendarEventsMergeWithRemindersInTimeOrder() {
        val day = startOfDay(now)
        val events = listOf(
            CalEvent(100, "Team sync", day + 16 * 3600_000L, day + 17 * 3600_000L, false, "Zoom"),
            CalEvent(101, "Dentist", day + 10 * 3600_000L, day + 11 * 3600_000L, false, "Indiranagar"),
            CalEvent(102, "Next week offsite", day + 7 * DAY, day + 8 * DAY, true),
        )
        val reminders = listOf(
            KeepReminder(1, "dentist", day + 10 * 3600_000L, "event", 3, hasTime = true),     // already on the calendar
            KeepReminder(2, "Harris Jayaraj concert", day + DAY + 19 * 3600_000L, "event", 5, hasTime = true),
        )
        val b = buildBrief(reminders, emptyList(), emptyList(), emptyList(), 0, emptyMap(), emptySet(), now, events = events)
        assertEquals(listOf("Dentist", "Team sync", "Harris Jayaraj concert"), b.today.map { it.text })
        assertEquals(101L, b.today.first().eventId)
        assertEquals("calendar · 10:00 am · Indiranagar", b.today.first().sub)
    }

    @Test
    fun emptyDayIsCalm() {
        val b = buildBrief(emptyList(), emptyList(), emptyList(), emptyList(), 0, emptyMap(), emptySet(), now)
        assertTrue(b.isEmpty)
        assertEquals("Nothing urgent today. Enjoy it.", b.summary())
    }
}
