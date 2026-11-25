package com.keepsakeai.app.expiry

import com.keepsakeai.app.data.preferences.KeepReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Test

class ExpiriesTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) =
        LocalDate.of(y, m, d).atTime(h, 0).atZone(zone.toZoneId()).toInstant().toEpochMilli()
    private val now = at(2026, 11, 25, 12)
    private fun r(id: Long, t: Long, kind: String = Expiries.KIND, status: String = "suggested") =
        KeepReminder(id = id, title = "item$id", atMillis = t, kind = kind, memoryId = id, status = status, hasTime = false)

    @Test fun findsDayMonthName() = assertEquals(at(2027, 3, 12), Expiries.find("Samsung TV. Warranty valid till 12 Mar 2027", now, zone))
    @Test fun findsMonthNameFirst() = assertEquals(at(2027, 3, 12), Expiries.find("Policy expires on March 12, 2027", now, zone))
    @Test fun findsDayFirstNumeric() = assertEquals(at(2027, 4, 5), Expiries.find("Warranty until 05/04/2027", now, zone))
    @Test fun numericMonthFirstWhenDayTooBig() = assertEquals(at(2027, 3, 25), Expiries.find("expiry date 03/25/2027", now, zone))
    @Test fun findsIso() = assertEquals(at(2027, 1, 31), Expiries.find("valid through 2027-01-31", now, zone))
    @Test fun ignoresPastDates() = assertNull(Expiries.find("Warranty valid till 12 Mar 2025", now, zone))
    @Test fun needsKeyword() = assertNull(Expiries.find("Bought on 12 Mar 2027 at Croma", now, zone))
    @Test fun skipsPastThenFindsNextKeyword() =
        assertNotNull(Expiries.find("warranty valid till 1 Jan 2020\nexpires 10 Dec 2026", now, zone))
    @Test fun daysLeftAndBuckets() {
        assertEquals(0, Expiries.daysLeft(r(1, at(2026, 11, 25)), now))
        assertEquals(-3, Expiries.daysLeft(r(2, at(2026, 11, 22)), now))
        assertEquals(Expiries.Bucket.EXPIRED, Expiries.bucket(-1))
        assertEquals(Expiries.Bucket.SOON, Expiries.bucket(30))
        assertEquals(Expiries.Bucket.LATER, Expiries.bucket(31))
    }
    @Test fun alertStages() {
        assertEquals("week", Expiries.alertStage(0)); assertEquals("week", Expiries.alertStage(7))
        assertEquals("month", Expiries.alertStage(8)); assertEquals("month", Expiries.alertStage(30))
        assertNull(Expiries.alertStage(31)); assertNull(Expiries.alertStage(-1))
    }
    @Test fun listSortsAndDropsOldAndDismissed() {
        val a = r(1, at(2027, 2, 1)); val b = r(2, at(2026, 12, 1)); val old = r(3, at(2026, 10, 1))
        val gone = r(4, at(2026, 12, 5), status = "dismissed"); val bill = r(5, at(2026, 12, 2), kind = "due")
        val recent = r(6, at(2026, 11, 20))
        assertEquals(listOf(6L, 2L, 1L), Expiries.list(listOf(a, b, old, gone, bill, recent), now).map { it.id })
    }
    @Test fun homeShowsOnlyNearExpiries() {
        assertTrue(Expiries.showOnHome(r(1, at(2026, 12, 10)), now))
        assertTrue(!Expiries.showOnHome(r(2, at(2027, 6, 1)), now))
        assertTrue(Expiries.showOnHome(r(3, at(2027, 6, 1), kind = "due"), now))
    }
}
