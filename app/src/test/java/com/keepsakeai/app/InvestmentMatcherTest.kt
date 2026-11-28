package com.keepsakeai.app

import com.keepsakeai.app.finance.InvestmentMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InvestmentMatcherTest {
    private val debit = "Debit INR 500000.00 Axis Bank A/c XX0284 28-11-26 10:14:09 MOB-TD/926040115388980/S V WhatsApp BAL to 917036165000 Not You? SMS BLOCKALL CustID to 919951860002"
    private val booked = "Your FD no. X8980 is booked for INR 500000 for a tenure of 12M 0D at 6.25%. Nomination Registered: Y, Auto Renewal: U - Axis Bank"

    @Test fun termDepositDebitIsAnInvestment() {
        assertTrue(InvestmentMatcher.isInvestment(debit))
        assertEquals("Fixed deposit · Axis Bank", InvestmentMatcher.label(debit, "S V"))
    }

    @Test fun otherInvestmentsAreRecognised() {
        assertTrue(InvestmentMatcher.isInvestment("Rs 5000 debited for SIP to ICCL mutual fund"))
        assertTrue(InvestmentMatcher.isInvestment("Paid Rs 2,000 to Zerodha Broking"))
        assertEquals("Mutual fund", InvestmentMatcher.label("SIP debit to ICCL", "ICCL"))
    }

    @Test fun ordinarySpendingIsNot() {
        assertFalse(InvestmentMatcher.isInvestment("INR 299.00 spent using ICICI Bank Card XX4321 on Google YouTube"))
        assertFalse(InvestmentMatcher.isInvestment("Paid Rs 450 to Swiggy via UPI"))
        assertFalse(InvestmentMatcher.isInvestment("Debit INR 2,000 to TDS payment for HDFC"))
    }

    @Test fun parsesTheFdBookingMessage() {
        val d = InvestmentMatcher.parseDeposit(booked, "AD-AXISBK-S")
        assertNotNull(d)
        d!!
        assertEquals("FD", d.kind)
        assertEquals("X8980", d.ref)
        assertEquals(500000.0, d.amount, 0.01)
        assertEquals(12, d.months)
        assertEquals(0, d.days)
        assertEquals(6.25, d.rate!!, 0.001)
        assertEquals("Axis Bank FD X8980 matures", d.title)
        val zone = ZoneId.of("Asia/Kolkata")
        val bookedAt = LocalDate.of(2026, 11, 28).atTime(10, 20).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2027, 11, 28), java.time.Instant.ofEpochMilli(d.maturityFrom(bookedAt, zone)).atZone(zone).toLocalDate())
    }

    @Test fun otherTenureForms() {
        assertEquals(24, InvestmentMatcher.parseDeposit("Fixed deposit opened for Rs 1,00,000, tenure 2 years at 7%")!!.months)
        assertEquals(400, InvestmentMatcher.parseDeposit("Your FD a/c 1234 has been booked: INR 50000, tenure 400 days")!!.days)
    }

    @Test fun notADepositBooking() {
        assertNull(InvestmentMatcher.parseDeposit("Payment of INR 45701.06 has been received towards your Axis Bank Credit Card XX3092"))
        assertNull(InvestmentMatcher.parseDeposit(debit))
    }
}
