package com.keepsakeai.app

import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.worker.ReminderWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderTimesTest {
    private fun t(day: Int, hour: Int, min: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.DECEMBER, day, hour, min, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val now = t(1, 10)

    @Test
    fun concertWithTimeGetsEveningBeforeAndHourBefore() {
        val r = KeepReminder(1, "Harris Jayaraj concert", t(14, 19), "event", 0, hasTime = true)
        assertEquals(listOf(t(13, 19), t(14, 18)), ReminderWorker.notifyTimesFor(r, now))
    }

    @Test
    fun dateOnlyPosterGetsEveningBeforeAndMorningOf() {
        val r = KeepReminder(2, "Harris Jayaraj concert", t(14, 9), "event", 0, hasTime = false)
        assertEquals(listOf(t(13, 19), t(14, 9)), ReminderWorker.notifyTimesFor(r, now))
    }

    @Test
    fun billGetsDayBeforeAndDayOfAt9() {
        val r = KeepReminder(3, "Pay BESCOM", t(5, 9), "due", 0, hasTime = false)
        assertEquals(listOf(t(4, 9), t(5, 9)), ReminderWorker.notifyTimesFor(r, now))
    }

    @Test
    fun pastTimesAreSkipped() {
        val r = KeepReminder(4, "Show tonight", t(1, 20), "event", 0, hasTime = true)
        assertEquals(listOf(t(1, 19)), ReminderWorker.notifyTimesFor(r, now))
    }
}
