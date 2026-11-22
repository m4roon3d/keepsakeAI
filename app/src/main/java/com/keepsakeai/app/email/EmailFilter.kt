package com.keepsakeai.app.email

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/** Decides which emails are worth reading and trims them to what matters. Pure, unit-tested. */
object EmailFilter {

    private val wanted = Regex(
        """\b(receipt|invoice|order|ordered|payment|paid|debited|charged|transaction|txn|ticket|tickets|booking|booked|reservation|confirmed|confirmation|itinerary|e-?ticket|pnr|boarding pass|bill|bills|due|statement|renew|renews|renewal|renewed|subscription|membership|shipped|dispatched|delivered|out for delivery|refund|premium|emi|appointment|check-?in|purchase|trial)\b""",
        RegexOption.IGNORE_CASE
    )

    private val unwanted = Regex(
        """(\b\d{1,2}\s?% off|\bflat\s?\d+\s?% |\bsale\b|\boffers?\b|\bdeals?\b|\bcoupons?\b|\bdiscounts?\b|\bcashback offer|webinar|newsletter|\bwin\b|last chance|hurry|limited time|\bOTP\b|verification code|one[- ]time password|password reset|reset your password|sign[- ]?in attempt|security alert|new (sign[- ]?in|login)|welcome to|verify your (email|account)|rate your|how was your|review your (order|purchase|experience))""",
        RegexOption.IGNORE_CASE
    )

    fun isWorthReading(subject: String, from: String = ""): Boolean {
        if (subject.isBlank()) return false
        if (unwanted.containsMatchIn(subject)) return false
        return wanted.containsMatchIn(subject)
    }

    private val footer = Regex(
        """(unsubscribe|privacy policy|all rights reserved|©|this (e-?mail|message) (was|is|has been) sent|you are receiving this|you received this|do not reply|please don't reply|download (our|the) app|follow us|view (it )?in (your )?browser|manage (your )?(email )?preferences)""",
        RegexOption.IGNORE_CASE
    )
    private val urlOnly = Regex("""^[<(\[]?https?://\S+[>)\]]?$""")

    /** Keeps the useful top of an email: no footers, bare links or blank runs, at most [max] characters. */
    fun cleanBody(text: String, max: Int = 1800): String {
        val out = StringBuilder()
        var blank = false
        for (raw in text.replace(' ', ' ').lines()) {
            val line = raw.replace(Regex("""[ \t]+"""), " ").trim()
            if (footer.containsMatchIn(line) && out.length > 80) break
            if (line.isEmpty() || urlOnly.matches(line)) {
                if (!blank && out.isNotEmpty()) { out.append('\n'); blank = true }
                continue
            }
            out.append(line).append('\n')
            blank = false
            if (out.length >= max) break
        }
        return out.toString().trim().take(max)
    }

    /** HTML email to readable lines (tables and blocks keep their breaks). */
    fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, head, title").remove()
        doc.select("br, p, div, tr, li, h1, h2, h3, h4, h5, table, section").forEach { it.before(TextNode("\n")) }
        doc.select("td, th").forEach { it.before(TextNode(" ")) }
        return doc.body()?.wholeText() ?: doc.wholeText()
    }

    /** Short display name from a From header like "Swiggy <noreply@swiggy.in>". */
    fun senderName(from: String): String {
        val name = from.substringBefore('<').trim().trim('"')
        if (name.isNotBlank()) return name
        val addr = from.substringAfter('<').substringBefore('>').trim()
        return addr.substringAfter('@').substringBefore('.').ifBlank { addr }
    }
}
