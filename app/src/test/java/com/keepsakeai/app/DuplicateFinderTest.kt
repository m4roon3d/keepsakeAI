package com.keepsakeai.app

import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.finance.DuplicateFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {
    private val t = 1_800_000_000_000L
    private val min = 60_000L
    private val hour = 60 * min

    private fun e(id: Long, amt: Double, src: String, at: Long, notes: String = "", merchant: String = "Swiggy") =
        ExpenseEntity(id = id, merchant = merchant, amount = amt, category = "Food", notes = notes, sourceMethod = src, timestamp = at)

    @Test
    fun smsAndEmailCopyOfTheSamePaymentCollapseKeepingTheSms() {
        val sms = e(1, 486.0, "UPI · HDFC Bank", t, "UTR: 432618904512")
        val mail = e(2, 486.0, "Email", t + 20 * min)
        val d = DuplicateFinder.find(listOf(mail, sms))
        assertEquals(1, d.size)
        assertEquals(2L, d[0].remove.id)
        assertEquals(1L, d[0].keep.id)
    }

    @Test
    fun twoMessagesWithDifferentUpiRefsAreDifferentPayments() {
        val a = e(1, 10.0, "UPI · HDFC Bank", t, "UTR: 111111111111")
        val b = e(2, 10.0, "UPI · HDFC Bank", t + min, "UTR: 222222222222")
        assertTrue(DuplicateFinder.find(listOf(a, b)).isEmpty())
    }

    @Test
    fun sameUpiRefTwiceIsADuplicateWhateverTheTime() {
        val a = e(1, 10.0, "UPI · HDFC Bank", t, "UTR: 111111111111")
        val b = e(2, 10.0, "UPI · ICICI Bank", t + 3 * hour, "UTR: 111111111111")
        assertEquals(1, DuplicateFinder.find(listOf(a, b)).size)
    }

    @Test
    fun twoMessagesWithoutRefsFarApartAreDifferent() {
        val a = e(1, 10.0, "Card · HDFC Bank", t)
        val b = e(2, 10.0, "Card · HDFC Bank", t + 20 * min)
        assertTrue(DuplicateFinder.find(listOf(a, b)).isEmpty())
    }

    @Test
    fun manualEntriesAreNeverTouched() {
        val a = e(1, 486.0, "Manual", t)
        val b = e(2, 486.0, "UPI · HDFC Bank", t + min)
        assertTrue(DuplicateFinder.find(listOf(a, b)).isEmpty())
    }

    @Test
    fun differentAmountsAreNeverDuplicates() {
        assertTrue(DuplicateFinder.find(listOf(e(1, 486.0, "Email", t), e(2, 487.0, "Email", t))).isEmpty())
    }

    @Test
    fun threeCopiesLeaveOne() {
        val sms = e(1, 99.0, "UPI · SBI", t, "UTR: 123456789012")
        val mail = e(2, 99.0, "Email", t + hour)
        val ocr = e(3, 99.0, "Screen Snap OCR", t + 2 * hour)
        val d = DuplicateFinder.find(listOf(sms, mail, ocr))
        assertEquals(setOf(2L, 3L), d.map { it.remove.id }.toSet())
    }

    private val sms = "INR 299.00 spent using ICICI Bank Card XX4321 on 27-Nov-26 on Google YouTube. Avl Limit: INR 1,52,340.00"

    @Test
    fun theSameSmsSavedAgainHoursLaterIsOnePayment() {
        val a = e(1, 299.0, "Card · ICICI Bank", t, merchant = "YouTube")
        val b = e(2, 299.0, "Card · ICICI Bank", t + 4 * hour, merchant = "YouTube")
        val c = e(3, 299.0, "Card · ICICI Bank", t + 5 * hour, merchant = "YouTube")
        val d = DuplicateFinder.find(listOf(a, b, c), mapOf(a.timestamp to sms, b.timestamp to sms, c.timestamp to "  " + sms.uppercase() + " "))
        assertEquals(setOf(2L, 3L), d.map { it.remove.id }.toSet())
        assertTrue(d.all { it.keep.id == 1L })
    }

    @Test
    fun twoRealChargesWithDifferentMessagesStaySeparate() {
        val a = e(1, 299.0, "Card · ICICI Bank", t, merchant = "YouTube")
        val b = e(2, 299.0, "Card · ICICI Bank", t + 4 * hour, merchant = "YouTube")
        val other = sms.replace("1,52,340.00", "1,52,041.00")
        assertTrue(DuplicateFinder.find(listOf(a, b), mapOf(a.timestamp to sms, b.timestamp to other)).isEmpty())
    }

    @Test
    fun withoutMessageTextHoursApartAreNotMerged() {
        val a = e(1, 299.0, "Card · ICICI Bank", t, merchant = "YouTube")
        val b = e(2, 299.0, "Card · ICICI Bank", t + 4 * hour, merchant = "YouTube")
        assertTrue(DuplicateFinder.find(listOf(a, b)).isEmpty())
    }

    @Test
    fun fingerprintIgnoresCaseAndSpacingButNotContent() {
        val f = com.keepsakeai.app.finance.MessageFingerprint
        assertEquals(f.of(sms), f.of(sms.lowercase().replace(" ", "  ")))
        assertTrue(f.of(sms) != f.of(sms.replace("299.00", "298.00")))
        assertEquals(null, f.of("short"))
    }
}
