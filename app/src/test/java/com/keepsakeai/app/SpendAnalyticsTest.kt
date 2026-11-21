package com.keepsakeai.app

import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.GroupBy
import com.keepsakeai.app.ui.spend.SpendFilter
import com.keepsakeai.app.ui.spend.SpendPeriod
import com.keepsakeai.app.ui.spend.applyFilter
import com.keepsakeai.app.ui.spend.breakdown
import com.keepsakeai.app.ui.spend.buckets
import com.keepsakeai.app.ui.spend.channelOf
import com.keepsakeai.app.ui.spend.grouped
import com.keepsakeai.app.ui.spend.inRange
import com.keepsakeai.app.ui.spend.percentChange
import com.keepsakeai.app.ui.spend.periodRange
import com.keepsakeai.app.ui.spend.previousRange
import com.keepsakeai.app.ui.spend.topMerchants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpendAnalyticsTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    /** 21 Nov 2026, 18:00 IST */
    private val now = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 21, 18, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private fun at(month: Int, day: Int, hour: Int = 12) =
        Calendar.getInstance(tz).apply { set(2026, month, day, hour, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private fun e(m: String, amt: Double, cat: String, src: String, t: Long) =
        ExpenseEntity(merchant = m, amount = amt, category = cat, sourceMethod = src, timestamp = t)

    private val data = listOf(
        e("Swiggy", 400.0, "Food", "UPI · ICICI Bank", at(Calendar.NOVEMBER, 20)),
        e("swiggy ", 250.0, "Food", "UPI (ICICI Bank)", at(Calendar.NOVEMBER, 5)),
        e("Uber", 300.0, "Transport", "Card · HDFC Bank", at(Calendar.NOVEMBER, 20)),
        e("Bescom", 1200.0, "Bills", "Manual", at(Calendar.NOVEMBER, 2)),
        e("Amazon", 999.0, "Shopping", "UPI · ICICI Bank", at(Calendar.OCTOBER, 10)),
        e("Zepto", 150.0, "Food", "UPI · SBI", at(Calendar.OCTOBER, 25)),
    )

    @Test
    fun channelParsing() {
        assertEquals("UPI", channelOf("UPI · ICICI Bank"))
        assertEquals("UPI", channelOf("UPI (ICICI Bank)"))
        assertEquals("Card", channelOf("Card · HDFC Bank"))
        assertEquals("Manual", channelOf("Manual"))
        assertEquals("Receipt", channelOf("CAMERA_SCAN"))
    }

    @Test
    fun thisMonthTotalsAndComparison() {
        val r = periodRange(SpendPeriod.THIS_MONTH, data, now, tz)
        assertEquals(2150.0, data.inRange(r).sumOf { it.amount }, 0.01)
        // Same point last month = 1 Oct .. 21 Oct 18:00 → Amazon only (Zepto is 25 Oct)
        val prev = previousRange(SpendPeriod.THIS_MONTH, now, tz)!!
        assertEquals(999.0, data.inRange(prev).sumOf { it.amount }, 0.01)
        assertEquals(115, percentChange(2150.0, 999.0))
        assertNull(percentChange(10.0, 0.0))
    }

    @Test
    fun lastMonthRange() {
        val r = periodRange(SpendPeriod.LAST_MONTH, data, now, tz)
        assertEquals(1149.0, data.inRange(r).sumOf { it.amount }, 0.01)
    }

    @Test
    fun filtersCombine() {
        val upiFood = data.applyFilter(SpendFilter(categories = setOf("Food"), channels = setOf("UPI")))
        assertEquals(3, upiFood.size)
        val swiggy = data.applyFilter(SpendFilter(merchant = "SWIGGY"))
        assertEquals(2, swiggy.size)
        assertEquals(1, data.applyFilter(SpendFilter(query = "besc")).size)
    }

    @Test
    fun groupingAndBreakdown() {
        val byCat = data.breakdown { it.category }
        assertEquals("Bills", byCat.first().first)
        val byMerchant = data.grouped(GroupBy.MERCHANT, tz)
        assertEquals(650.0, byMerchant.first { it.key == "swiggy" }.total, 0.01)
        val byDay = data.grouped(GroupBy.DAY, tz)
        assertEquals(700.0, byDay.first().total, 0.01) // 20 Nov: Swiggy + Uber
        val top = data.topMerchants(2)
        assertEquals("Bescom", top[0].name)
        assertEquals(2, top.size)
    }

    @Test
    fun bucketsAreDailyForAMonthAndWeeklyBeyond() {
        val month = periodRange(SpendPeriod.THIS_MONTH, data, now, tz)
        val daily = data.buckets(month, tz)
        assertEquals(21, daily.size)
        assertEquals(700.0, daily[19].total, 0.01)
        val ninety = periodRange(SpendPeriod.LAST_90, data, now, tz)
        val weekly = data.buckets(ninety, tz)
        assertEquals(13, weekly.size)
        assertEquals(data.sumOf { it.amount }, weekly.sumOf { it.total }, 0.01)
        assertEquals(DAY * 7, weekly[1].start - weekly[0].start)
    }
}
