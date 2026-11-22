package com.keepsakeai.app

import com.keepsakeai.app.email.EmailFilter
import com.keepsakeai.app.email.MailProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFilterTest {

    @Test fun readsReceiptsTicketsBillsAndRenewals() {
        listOf(
            "Your Swiggy order receipt",
            "Payment received for your Airtel bill",
            "Your Netflix membership has been renewed",
            "Booking Confirmed: PNR 4521987650",
            "Your Amazon.in order #403-1234567 has shipped",
            "Your HDFC Credit Card statement for November",
            "E-ticket for Harris Jayaraj Live in Chennai",
            "BESCOM bill due on 28 Nov",
        ).forEach { assertTrue(it, EmailFilter.isWorthReading(it)) }
    }

    @Test fun skipsPromosAndSecurityMail() {
        listOf(
            "Flat 50% off on your next order!",
            "Big Billion Days sale is live",
            "Your OTP for login is 482913",
            "Security alert: new sign-in from Chrome",
            "Welcome to Swiggy One",
            "Weekly newsletter: 10 things to read",
            "How was your order? Rate your experience",
            "Lunch on Sunday?",
            "",
        ).forEach { assertFalse(it, EmailFilter.isWorthReading(it)) }
    }

    @Test fun cleanBodyDropsFootersAndBareLinks() {
        val raw = """
            Hi Ramanaa,

            Thanks for your order. Amount paid: Rs 349.00
            Order ID 12345 · Delivered to Indiranagar


            https://swiggy.com/track/12345
            Need help? Contact support.
            You are receiving this email because you ordered on Swiggy. Unsubscribe here.
            Bundl Technologies, all rights reserved
        """.trimIndent()
        val clean = EmailFilter.cleanBody(raw)
        assertTrue(clean.contains("Amount paid: Rs 349.00"))
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("Unsubscribe"))
        assertFalse(clean.contains("\n\n\n"))
    }

    @Test fun htmlKeepsLineBreaks() {
        val text = EmailFilter.htmlToText("<html><body><table><tr><td>Item</td><td>Rs 199</td></tr><tr><td>Total</td><td>Rs 199</td></tr></table><p>Thanks</p></body></html>")
        val clean = EmailFilter.cleanBody(text)
        assertEquals(listOf("Item Rs 199", "Total Rs 199", "Thanks"), clean.lines())
    }

    @Test fun senderNames() {
        assertEquals("Swiggy", EmailFilter.senderName("Swiggy <noreply@swiggy.in>"))
        assertEquals("irctc", EmailFilter.senderName("<ticketadmin@irctc.co.in>"))
    }

    @Test fun providerFromAddress() {
        assertEquals(MailProvider.GMAIL, MailProvider.forAddress("me@gmail.com"))
        assertEquals(MailProvider.YAHOO, MailProvider.forAddress("me@yahoo.co.in"))
        assertEquals(null, MailProvider.forAddress("me@company.com"))
        assertTrue(MailProvider.isMicrosoft("me@hotmail.com"))
        assertFalse(MailProvider.isMicrosoft("me@gmail.com"))
    }
}
