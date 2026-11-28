package com.keepsakeai.app.finance

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Money put away rather than spent: fixed and recurring deposits, mutual funds and SIPs, stocks, NPS, PPF, gold bonds.
 * These are kept (under Investments) but never counted as spending. Pure and unit-tested.
 */
object InvestmentMatcher {
    private val investment = Regex(
        "\\b(?:mob|ib|net|inb|nb)-?td\\b|\\btd/\\d|term deposit|fixed deposit|recurring deposit|\\bfd (?:no|a/c|booking|booked|opened)|" +
            "\\brd (?:no|a/c|instal+ment)|mutual fund|\\bsip\\b|systematic investment|\\bnps\\b|\\bppf\\b|sovereign gold|\\bsgb\\b|" +
            "\\biccl\\b|indian clearing|\\bnsccl\\b|bse ?star|\\bbse ltd|nse clearing|zerodha|groww|kuvera|upstox|angel ?one|" +
            "paytm money|indmoney|smallcase|\\bcams\\b|kfin ?tech|mf ?utilities|coin by zerodha",
        RegexOption.IGNORE_CASE
    )

    fun isInvestment(text: String): Boolean = investment.containsMatchIn(text)

    private val banks = listOf(
        "Axis", "HDFC", "ICICI", "SBI", "Kotak", "IDFC", "Yes Bank", "IndusInd", "Federal", "AU", "Canara",
        "Bank of Baroda", "PNB", "Union Bank", "Bandhan", "RBL", "IDBI"
    )

    /** "Axis Bank" from the sender or text, or null. */
    fun bankOf(text: String): String? {
        val t = " " + text.replace(Regex("[^A-Za-z ]"), " ") + " "
        val hit = banks.firstOrNull { Regex("\\b${Regex.escape(it)}", RegexOption.IGNORE_CASE).containsMatchIn(t) }
            ?: if (Regex("axisbk", RegexOption.IGNORE_CASE).containsMatchIn(text)) "Axis" else null
        return hit?.let { if (it.endsWith("Bank")) it else "$it Bank" }
    }

    /** A short name for the Investments list: "Fixed deposit · Axis Bank", "Mutual fund", or the payee. */
    fun label(text: String, fallback: String): String {
        val t = text.lowercase(Locale.ROOT)
        val kind = when {
            Regex("\\b(?:mob|ib|net|inb|nb)-?td\\b|\\btd/\\d|term deposit|fixed deposit|\\bfd\\b").containsMatchIn(t) -> "Fixed deposit"
            Regex("recurring deposit|\\brd\\b").containsMatchIn(t) -> "Recurring deposit"
            Regex("mutual fund|\\bsip\\b|systematic|iccl|indian clearing|bse ?star|cams|kfin|mf ?utilities|kuvera|coin by").containsMatchIn(t) -> "Mutual fund"
            Regex("\\bnps\\b").containsMatchIn(t) -> "NPS"
            Regex("\\bppf\\b").containsMatchIn(t) -> "PPF"
            Regex("sovereign gold|\\bsgb\\b").containsMatchIn(t) -> "Gold bond"
            Regex("zerodha|groww|upstox|angel ?one|nsccl|nse clearing|bse ltd|indmoney|smallcase|paytm money").containsMatchIn(t) -> "Stocks"
            else -> null
        } ?: return fallback
        return bankOf(text)?.let { "$kind · $it" } ?: kind
    }

    // ── A deposit being opened: "Your FD no. X8980 is booked for INR 500000 for a tenure of 12M 0D at 6.25%" ──

    data class Deposit(val kind: String, val ref: String?, val amount: Double, val months: Int, val days: Int, val rate: Double?, val bank: String?) {
        /** When it matures, from the day it was booked. */
        fun maturityFrom(bookedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(bookedAt).atZone(zone).toLocalDate().plusMonths(months.toLong()).plusDays(days.toLong())
                .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

        val title: String get() = listOfNotNull(bank, if (kind == "RD") "RD" else "FD", ref).joinToString(" ") + " matures"
    }

    private val depositKind = Regex("\\b(fd|rd|fixed deposit|term deposit|recurring deposit|deposit)\\b", RegexOption.IGNORE_CASE)
    private val opened = Regex("\\b(booked|opened|created|placed)\\b", RegexOption.IGNORE_CASE)
    private val amountRx = Regex("(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val refRx = Regex("\\b(?:fd|rd|deposit)\\s*(?:no\\.?|number|a/c(?:\\s*no\\.?)?)\\s*:?\\s*([A-Z0-9]{3,})", RegexOption.IGNORE_CASE)
    private val tenureMD = Regex("(\\d{1,3})\\s*m(?:onths?)?\\s*(\\d{1,3})\\s*d(?:ays?)?\\b", RegexOption.IGNORE_CASE)
    private val tenureY = Regex("(\\d{1,2})\\s*y(?:ears?|rs?)?\\b", RegexOption.IGNORE_CASE)
    private val tenureM = Regex("(\\d{1,3})\\s*months?\\b", RegexOption.IGNORE_CASE)
    private val tenureD = Regex("(\\d{1,4})\\s*days?\\b", RegexOption.IGNORE_CASE)
    private val rateRx = Regex("([\\d]{1,2}(?:\\.\\d{1,2})?)\\s*%")

    fun parseDeposit(body: String, sender: String = ""): Deposit? {
        val k = depositKind.find(body) ?: return null
        if (!opened.containsMatchIn(body)) return null
        val amount = amountRx.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val tenureText = body.substringAfter("tenure", body)
        var months = 0
        var days = 0
        tenureMD.find(tenureText)?.let { months = it.groupValues[1].toInt(); days = it.groupValues[2].toInt() }
            ?: tenureY.find(tenureText)?.let { months = it.groupValues[1].toInt() * 12 }
            ?: tenureM.find(tenureText)?.let { months = it.groupValues[1].toInt() }
            ?: tenureD.find(tenureText)?.let { days = it.groupValues[1].toInt() }
        if (months == 0 && days == 0) return null
        val kind = if (k.value.lowercase(Locale.ROOT).let { it == "rd" || it.startsWith("recurring") }) "RD" else "FD"
        return Deposit(
            kind, refRx.find(body)?.groupValues?.get(1)?.uppercase(Locale.ROOT), amount, months, days,
            rateRx.find(body)?.groupValues?.get(1)?.toDoubleOrNull(), bankOf("$sender $body")
        )
    }
}
