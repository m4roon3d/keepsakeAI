package com.keepsakeai.app

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.finance.ImportSection
import com.keepsakeai.app.finance.ImportSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSectionsTest {
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun mem(
        title: String, raw: String = "", source: CaptureSource = CaptureSource.EMAIL, ageHours: Long = 100, id: Long = 1, tags: String = "",
    ) = MemoryEntity(id = id, title = title, rawContent = raw, aiSummary = "", collectionName = "Receipts", source = source, tagsCsv = tags, createdAt = now - ageHours * hour)

    @Test
    fun smsImportsAreUpi() {
        assertEquals(ImportSection.UPI, ImportSections.of(mem("Swiggy", source = CaptureSource.UPI_MESSAGE)))
    }

    @Test
    fun mutualFundMailGoesToMf() {
        val m = mem("CAMS consolidated account statement", "Folio 1234567 SIP instalment of Rs 5000 allotted units")
        assertEquals(ImportSection.MF, ImportSections.of(m))
    }

    @Test
    fun brokerAndCryptoMailGoesToInvestments() {
        assertEquals(ImportSection.INVESTMENTS, ImportSections.of(mem("Zerodha contract note for 12 Nov")))
        assertEquals(ImportSection.INVESTMENTS, ImportSections.of(mem("Your CoinDCX withdrawal", "Bitcoin sent")))
    }

    @Test
    fun bankAndCardStatementsGoToBank() {
        assertEquals(ImportSection.BANK, ImportSections.of(mem("ICICI Bank credit card statement")))
        assertEquals(ImportSection.BANK, ImportSections.of(mem("Alert", "Acct XX123 debited via NEFT")))
    }

    @Test
    fun upiMailGoesToUpi() {
        assertEquals(ImportSection.UPI, ImportSections.of(mem("Payment received", "Paid via GPay UPI")))
    }

    @Test
    fun otherMailIsOther() {
        assertEquals(ImportSection.OTHER, ImportSections.of(mem("Your order has shipped", "Your parcel is in stock and on its way")))
    }

    @Test
    fun handCapturedMemoriesAreNotImports() {
        assertNull(ImportSections.of(mem("Bank holiday note", source = CaptureSource.TEXT_NOTE)))
    }

    @Test
    fun feedShowsHandCapturedAlways() {
        assertTrue(ImportSections.showInFeed(mem("note", source = CaptureSource.TEXT_NOTE, ageHours = 5000), now, emptySet()))
    }

    @Test
    fun feedHidesOldImportsButKeepsNewAndRelevantOnes() {
        assertFalse(ImportSections.showInFeed(mem("old statement", ageHours = 100), now, emptySet()))
        assertTrue(ImportSections.showInFeed(mem("new receipt", ageHours = 10), now, emptySet()))
        assertTrue(ImportSections.showInFeed(mem("train ticket", ageHours = 500, id = 7), now, setOf(7L)))
        assertFalse(ImportSections.showInFeed(mem("other", ageHours = 500, id = 8), now, setOf(7L)))
    }
}
