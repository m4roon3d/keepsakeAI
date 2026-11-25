package com.keepsakeai.app

import com.keepsakeai.app.feed.DateWindow
import com.keepsakeai.app.feed.DateWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateWindowsTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private val day = 24L * 60 * 60 * 1000
    private val now = Calendar.getInstance(zone).apply { set(2026, Calendar.NOVEMBER, 25, 7, 10, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val midnight = Calendar.getInstance(zone).apply { set(2026, Calendar.NOVEMBER, 25, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun todayYesterdayAndWeekRanges() {
        assertEquals(midnight until midnight + day, DateWindows.range(DateWindow.TODAY, now, zone))
        assertEquals(midnight - day until midnight, DateWindows.range(DateWindow.YESTERDAY, now, zone))
        assertEquals(midnight - 6 * day until midnight + day, DateWindows.range(DateWindow.WEEK, now, zone))
        assertTrue(now in DateWindows.range(DateWindow.TODAY, now, zone))
        assertTrue(midnight - 1 !in DateWindows.range(DateWindow.TODAY, now, zone))
    }

    @Test fun findsDateWordsAndKeepsTheRestOfTheQuery() {
        assertEquals(DateWindow.YESTERDAY to "", DateWindows.detect("yesterday"))
        assertEquals(DateWindow.TODAY to "bank mails", DateWindows.detect("bank mails from today"))
        assertEquals(DateWindow.WEEK to "ticket", DateWindows.detect("ticket this week"))
        assertEquals(DateWindow.WEEK to "", DateWindows.detect("past week"))
        assertEquals(DateWindow.YESTERDAY to "biryani", DateWindows.detect("biryani yesterday"))
    }

    @Test fun plainQueriesAreUntouched() {
        val (w, rest) = DateWindows.detect("  linto coffee ")
        assertNull(w)
        assertEquals("linto coffee", rest)
        assertNull(DateWindows.detect("weekly review").first)
    }
}
