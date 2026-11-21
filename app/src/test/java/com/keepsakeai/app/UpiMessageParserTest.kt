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
        assertEquals("HDFC Bank", parsed.bankOrApp)
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
}
