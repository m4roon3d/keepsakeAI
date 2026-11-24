package com.keepsakeai.app

import com.keepsakeai.app.capture.UpiMessageParser
import com.keepsakeai.app.finance.TransferMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two ICICI messages a real own-account transfer produced (sender BP-ICICIT-S). */
class IciciSelfTransferTest {
    private val credit = "ICICI Bank Account XX725 credited:Rs. 20,000.00 on 20-Nov-26. Info INF*INFT*000096209806*SELF*S. Available Balance is Rs. 23,665.87."
    private val debit = "ICICI Bank Acc XX029 debited Rs. 20,000.00 on 20-Nov-26 InfoINF*INFT*0000.Avl Bal Rs. 25,51,723.72.To dispute call 18002662 or SMS BLOCK 029 to 9215676766"

    @Test
    fun theAccDebitIsRead() {
        val d = UpiMessageParser.parse("BP-ICICIT-S", debit)
        assertNotNull(d)
        assertEquals(20000.0, d!!.amount, 0.001)
        assertEquals("029", d.account)
        assertEquals("ICICI Bank", d.bankOrApp.substringAfter(" · "))
    }

    @Test
    fun theCreditIsReadAndIsNotADebit() {
        assertNull(UpiMessageParser.parse("BP-ICICIT-S", credit))
        val c = UpiMessageParser.parseCredit("BP-ICICIT-S", credit)
        assertNotNull(c)
        assertEquals(20000.0, c!!.amount, 0.001)
        assertEquals("725", c.account)
    }

    @Test
    fun theDebitIsNotACredit() {
        assertNull(UpiMessageParser.parseCredit("BP-ICICIT-S", debit))
    }

    @Test
    fun theTwoMessagesPairUpAsATransferWithoutAnyUpiRef() {
        val t = 1_800_000_000_000L
        val d = UpiMessageParser.parse("BP-ICICIT-S", debit)!!
        val c = UpiMessageParser.parseCredit("BP-ICICIT-S", credit)!!
        val debitSide = TransferMatcher.Debit(d.amount, d.utrRef, d.account, d.toAccount, d.merchant, t + 20_000)
        val creditSide = TransferMatcher.Credit(c.amount, c.utrRef, c.account, t)
        assertTrue(TransferMatcher.isTransfer(debitSide, listOf(creditSide), emptySet(), emptyList()))
    }

    private val axisCardBill = "Debit INR 45701.06\nAxis Bank A/c XX0284\n19-11-26 17:01:15\nCreditCard Payment XX 3092\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002"

    @Test
    fun axisCreditCardBillPaymentIsReadAsACardBill() {
        val p = UpiMessageParser.parse("JK-AXISBK-S", axisCardBill)
        assertNotNull(p)
        assertEquals(45701.06, p!!.amount, 0.001)
        assertEquals("Card bill", p.category)
        assertEquals("Credit card bill XX3092", p.merchant)
        assertEquals("0284", p.account)
        assertEquals("3092", p.toAccount)
    }

    @Test
    fun cardBillWordingIsRecognisedInEmailsToo() {
        assertTrue(TransferMatcher.isCardBill("HDFC Bank Credit Card Payment received"))
        assertTrue(TransferMatcher.isCardBill("ICICI credit card bill"))
        assertTrue(!TransferMatcher.isCardBill("Amazon Pay"))
        assertTrue(!TransferMatcher.isCardBill("Debit card replaced"))
    }
}
