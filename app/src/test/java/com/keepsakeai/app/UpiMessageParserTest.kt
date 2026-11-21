package com.keepsakeai.app

import com.keepsakeai.app.capture.UpiMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpiMessageParserTest {

    @Test
    fun parsesHdfcUpiDebitWithUtr() {
        val sms = "Sent Rs.486.00 from HDFC Bank A/c *4821 to Swiggy Instamart via UPI. Ref No 432618904512."
        val parsed = UpiMessageParser.parse("HDFCBK", sms)
        assertNotNull(parsed)
        assertEquals(486.0, parsed!!.amount, 0.01)
        assertEquals("Food", parsed.category)
        assertEquals("UPI · HDFC Bank", parsed.bankOrApp)
        assertEquals("Swiggy Instamart", parsed.merchant)
        assertEquals("432618904512", parsed.utrRef)
    }

    @Test
    fun parsesGooglePayTransportTransaction() {
        val msg = "Paid ₹145 to Namma Yatri Auto using UPI (SBI *9012). UPI Ref: 432619112084."
        val parsed = UpiMessageParser.parse("Google Pay", msg)
        assertNotNull(parsed)
        assertEquals(145.0, parsed!!.amount, 0.01)
        assertEquals("Transport", parsed.category)
        assertEquals("432619112084", parsed.utrRef)
    }

    @Test
    fun ignoresPromotionalOrNonDebitSms() {
        val promo = "Get flat Rs.500 cashback on your next UPI payment this weekend!"
        val parsed = UpiMessageParser.parse("PAYTM", promo)
        assertNull(parsed)
    }

    @Test
    fun ignoresOtpMessages() {
        val otp = "123456 is your OTP for txn of Rs.2,499.00 at AMAZON on HDFC Bank card. Do not share."
        assertNull(UpiMessageParser.parse("HDFCBK", otp))
    }

    @Test
    fun ignoresCollectRequests() {
        val req = "Rs.300 will be debited from your A/c on approving the UPI collect request from merchant@ybl."
        assertNull(UpiMessageParser.parse("ICICIB", req))
    }

    @Test
    fun parsesPlainBankDebitWithoutUpiWord() {
        val sms = "Rs 1,250.00 debited from A/c XX4821 on 20-11-26 at BESCOM. Avl Bal Rs 10,000."
        val parsed = UpiMessageParser.parse("AX-SBIINB", sms)
        assertNotNull(parsed)
        assertEquals(1250.0, parsed!!.amount, 0.01)
        assertEquals("Bills", parsed.category)
    }

    @Test
    fun iciciUpiDebitIgnoresSmsBlockNumberAndReadsUpiRef() {
        val sms = "ICICI Bank Acct XX821 debited for Rs 176.00 on 20-Nov-26; COFFEE DAY credited. UPI:432618904512. Call 18002662 for dispute. SMS BLOCK 821 to 9215676766."
        val p = UpiMessageParser.parse("AX-ICICIT", sms)
        assertNotNull(p)
        assertEquals(176.0, p!!.amount, 0.01)
        assertEquals("Coffee Day", p.merchant)
        assertEquals("432618904512", p.utrRef)
        assertEquals("UPI · ICICI Bank", p.bankOrApp)
        assertEquals("Food", p.category)
    }

    @Test
    fun phoneNumberPayeeFallsBackToBankName() {
        val sms = "ICICI Bank Acct XX821 debited for Rs 226.00 on 20-Nov-26; 9876543210 credited. UPI:432618904999. Call 18002662 for dispute. SMS BLOCK 821 to 9215676766."
        val p = UpiMessageParser.parse("AX-ICICIT", sms)
        assertNotNull(p)
        assertEquals("ICICI Bank payment", p!!.merchant)
    }

    @Test
    fun neverTakesAvailableBalanceAsAmount() {
        val sms = "Avl Bal Rs 2,45,000.00. Your A/c XX12 debited for INR 499.00 at NETFLIX on 21-11-26."
        val p = UpiMessageParser.parse("JD-HDFCBK", sms)
        assertNotNull(p)
        assertEquals(499.0, p!!.amount, 0.01)
        assertEquals("Entertainment", p.category)
    }

    @Test
    fun sbiUpiInfoFormat() {
        val sms = "Dear UPI user A/C X1234 debited by 60.0 on date 20Nov26 trf to ZEPTO MARKETPLACE Refno 432600011122. If not u? call 1800111109 -SBI"
        val p = UpiMessageParser.parse("VM-SBIUPI", sms)
        assertNotNull(p)
        assertEquals(60.0, p!!.amount, 0.01)
        assertEquals("Zepto Marketplace", p.merchant)
        assertEquals("432600011122", p.utrRef)
        assertEquals("Food", p.category)
    }
}
