package com.keepsakeai.app.capture

import java.util.Locale

data class ParsedUpiTransaction(
    val merchant: String,
    val amount: Double,
    val currency: String = "INR",
    val category: String,
    val bankOrApp: String,
    val utrRef: String?,
    val rawSnippet: String
)

object UpiMessageParser {

    private val debitKeywords = listOf("debited", "sent", "paid", "spent", "withdrawn", "txn of", "transferred")
    private val upiIndicators = listOf("upi", "vpa", "gpay", "phonepe", "paytm", "bhim", "ref no", "utr")

    private val amountRegex = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val merchantRegex = Regex(
        """(?:to|at|vpa|info:?\s*upi/?[a-z0-9]*/?)\s+([A-Za-z0-9@._\-\s]{2,36}?)(?=\s+(?:on|via|ref|utr|upi|avl|bal|from|using)|[.,;\n]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val utrRegex = Regex(
        """(?:utr|ref(?:\s*no)?|upi\s*ref)[:\s#-]*([0-9]{10,16})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): ParsedUpiTransaction? {
        val lower = body.lowercase(Locale.US)
        val isDebit = debitKeywords.any { it in lower }
        val isUpi = upiIndicators.any { it in lower } || sender.lowercase(Locale.US).contains("upi")
        if (!isDebit || !isUpi) return null

        val rawAmt = amountRegex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")
        val amount = rawAmt?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val merchantRaw = merchantRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?: sender.ifBlank { "UPI Payee" }

        val utr = utrRegex.find(body)?.groupValues?.getOrNull(1)
        val category = inferCategory(merchantRaw, lower)
        val bank = detectBank(sender, lower)

        return ParsedUpiTransaction(
            merchant = merchantRaw.take(36),
            amount = amount,
            currency = "INR",
            category = category,
            bankOrApp = bank,
            utrRef = utr,
            rawSnippet = body.trim().take(200)
        )
    }

    private fun inferCategory(merchant: String, lowerBody: String): String {
        val combined = "${merchant.lowercase(Locale.US)} $lowerBody"
        return when {
            listOf("swiggy", "zomato", "coffee", "cafe", "blinkit", "zepto", "bigbasket", "biryani", "restaurant", "bakery").any { it in combined } -> "Food"
            listOf("uber", "ola", "rapido", "namma yatri", "metro", "irctc", "indigo", "petrol", "fuel", "shell", "hpcl").any { it in combined } -> "Transport"
            listOf("amazon", "flipkart", "myntra", "nykaa", "ajio", "decathlon", "croma", "mall").any { it in combined } -> "Shopping"
            listOf("bescom", "airtel", "jio", "act fibernet", "electricity", "broadband", "recharge", "bill").any { it in combined } -> "Bills"
            else -> "Other"
        }
    }

    private fun detectBank(sender: String, lowerBody: String): String {
        val s = "${sender.lowercase(Locale.US)} $lowerBody"
        return when {
            "hdfc" in s -> "HDFC Bank"
            "icici" in s -> "ICICI Bank"
            "sbi" in s -> "SBI"
            "axis" in s -> "Axis Bank"
            "kotak" in s -> "Kotak"
            "gpay" in s || "google pay" in s -> "Google Pay"
            "phonepe" in s -> "PhonePe"
            "paytm" in s -> "Paytm"
            else -> sender.ifBlank { "UPI SMS" }
        }
    }
}
