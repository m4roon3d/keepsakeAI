package com.keepsakeai.app

import com.keepsakeai.app.capture.UpiMessageParser
import com.keepsakeai.app.finance.TransferMatcher
import com.keepsakeai.app.finance.TransferMatcher.Credit
import com.keepsakeai.app.finance.TransferMatcher.Debit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferMatcherTest {
    private val t0 = 1_700_000_000_000L
    private val min = 60_000L

    private fun debit(amount: Double = 5000.0, utr: String? = "432618904512", account: String? = "123", to: String? = null, merchant: String = "John Doe", at: Long = t0) =
        Debit(amount, utr, account, to, merchant, at)

    @Test
    fun sameUpiRefOnDebitAndCreditIsATransfer() {
        val credits = listOf(Credit(5000.0, "432618904512", "456", t0 + 20_000))
        assertTrue(TransferMatcher.creditMatches(debit(), credits))
    }

    @Test
    fun differentUpiRefsAreNotAMatchEvenWithSameAmount() {
        val credits = listOf(Credit(5000.0, "999999999999", "456", t0 + min))
        assertFalse(TransferMatcher.creditMatches(debit(), credits))
    }

    @Test
    fun withoutRefsSameAmountWithinFiveMinutesMatches() {
        val credits = listOf(Credit(5000.0, null, "456", t0 + 2 * min))
        assertTrue(TransferMatcher.creditMatches(debit(utr = null), credits))
        val late = listOf(Credit(5000.0, null, "456", t0 + 9 * min))
        assertFalse(TransferMatcher.creditMatches(debit(utr = null), late))
    }

    @Test
    fun creditIntoTheSameAccountIsARefundNotATransfer() {
        val credits = listOf(Credit(5000.0, null, "123", t0 + min))
        assertFalse(TransferMatcher.creditMatches(debit(utr = null), credits))
    }

    @Test
    fun differentAmountNeverMatches() {
        val credits = listOf(Credit(4999.0, null, "456", t0))
        assertFalse(TransferMatcher.creditMatches(debit(utr = null), credits))
    }

    @Test
    fun destinationAccountThatIsOwnIsATransfer() {
        val d = debit(utr = null, to = "456")
        assertTrue(TransferMatcher.namesOwnAccount(d, setOf("123", "456")))
        assertFalse(TransferMatcher.namesOwnAccount(d, setOf("123")))
        // naming the source account as destination is not a transfer signal
        assertFalse(TransferMatcher.namesOwnAccount(debit(to = "123"), setOf("123")))
    }

    @Test
    fun payeeThatIsTheUsersOwnNameOrUpiIdIsATransfer() {
        assertTrue(TransferMatcher.isOwnPayee("Ramanaa Kumar", listOf("ramanaa kumar")))
        assertTrue(TransferMatcher.isOwnPayee("KUMAR RAMANAA", listOf("Ramanaa Kumar")))
        assertTrue(TransferMatcher.isOwnPayee("ramanaa.k", listOf("ramanaa.k@okicici")))
        assertFalse(TransferMatcher.isOwnPayee("Swiggy", listOf("Ramanaa Kumar")))
        assertFalse(TransferMatcher.isOwnPayee("Ram", listOf("Ramanaa Kumar")))
        assertFalse(TransferMatcher.isOwnPayee("Anyone", emptyList()))
    }

    @Test
    fun iciciInternalTransferSmsPairIsDetectedEndToEnd() {
        val debitSms = "ICICI Bank Acct XX123 debited for Rs 5,000.00 on 12-Nov-26; JOHN DOE credited. UPI:432618904512. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766."
        val creditSms = "Dear Customer, Acct XX456 is credited with Rs 5,000.00 on 12-Nov-26 from JOHN DOE. UPI:432618904512-ICICI Bank."

        val d = UpiMessageParser.parse("AX-ICICIT", debitSms)
        assertNotNull(d)
        assertEquals(5000.0, d!!.amount, 0.01)
        assertEquals("123", d.account)
        assertEquals("432618904512", d.utrRef)

        // The credit message is not a debit, and the debit message is not a credit.
        assertNull(UpiMessageParser.parse("AX-ICICIT", creditSms))
        assertNull(UpiMessageParser.parseCredit("AX-ICICIT", debitSms))

        val c = UpiMessageParser.parseCredit("AX-ICICIT", creditSms)
        assertNotNull(c)
        assertEquals(5000.0, c!!.amount, 0.01)
        assertEquals("456", c.account)
        assertEquals("432618904512", c.utrRef)

        val debit = Debit(d.amount, d.utrRef, d.account, d.toAccount, d.merchant, t0)
        assertTrue(TransferMatcher.isTransfer(debit, listOf(Credit(c.amount, c.utrRef, c.account, t0 + 30_000)), emptySet(), emptyList()))
    }

    @Test
    fun parserReadsAccountDigits() {
        assertEquals(listOf("4821"), UpiMessageParser.accounts("Sent Rs.486.00 from HDFC Bank A/c *4821 to Swiggy Instamart via UPI."))
        assertEquals(listOf("9012"), UpiMessageParser.accounts("Paid ₹145 to Namma Yatri Auto using UPI (SBI *9012)."))
        assertEquals(listOf("123", "456"), UpiMessageParser.accounts("Rs 2000 debited from A/c XX123 and credited to A/c XX456"))
        assertTrue(UpiMessageParser.accounts("Paid Rs 500 to Max 1234 Stores").isEmpty())
    }

    @Test
    fun promoAndOtpCreditsAreNotCredits() {
        assertNull(UpiMessageParser.parseCredit("PAYTM", "Get Rs.500 cashback credited to your wallet on next UPI payment!"))
        assertNull(UpiMessageParser.parseCredit("ICICIB", "OTP 123456 for Rs 5000 credited to A/c XX456 via UPI"))
    }
}
