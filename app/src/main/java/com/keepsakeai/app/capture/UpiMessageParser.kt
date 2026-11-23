package com.keepsakeai.app.capture

import java.util.Locale

data class ParsedUpiTransaction(
    val merchant: String,
    val amount: Double,
    val currency: String = "INR",
    val category: String,
    /** "UPI · ICICI Bank", "Card · HDFC Bank", "Bank transfer · SBI"… */
    val bankOrApp: String,
    val utrRef: String?,
    val rawSnippet: String,
    val channel: String = "UPI",
    /** Last digits of the account the money left from ("A/c *4821"), when the message says. */
    val account: String? = null,
    /** Last digits of a second account named in the message, usually where the money went. */
    val toAccount: String? = null,
)

/** A credit message. Never saved as an expense; only used to recognise transfers between the user's own accounts. */
data class ParsedCredit(
    val amount: Double,
    val utrRef: String?,
    val account: String?,
)

/**
 * Reads Indian bank / UPI debit messages.
 *
 * Steps: drop non-debits (OTP, collect requests, credits, promos) → cut the
 * boilerplate tail ("SMS BLOCK… to 9215676766", "Call… for dispute") → take the
 * debited amount (never "Avl Bal" or "limit") → find the payee with bank-specific
 * patterns → read the UPI ref / RRN → classify the channel (UPI, card, transfer, ATM).
 */
object UpiMessageParser {

    private val debitKeywords = listOf("debited", "sent", "paid", "spent", "withdrawn", "txn of", "transferred", "purchase", "payment of")
    private val moneyIndicators = listOf("upi", "vpa", "gpay", "phonepe", "paytm", "bhim", "ref no", "utr", "rrn", "a/c", "acct", "account", "card", "imps", "neft", "rtgs")

    /** Messages that mention money but aren't completed debits. */
    private val ignoreKeywords = listOf(
        "otp", "one time password", "verification code", "requested", "request from", "collect request",
        "will be debited", "is due", "due on", "due by", "reminder", "cashback", "offer", "win ", "loan",
        "credited to your", "has been credited", "is credited", "received from", "refund", "reversed",
        "failed", "declined", "unsuccessful", "emi of", "mandate", "autopay set", "pre-approved"
    )

    /** Boilerplate that bank SMS append after the useful part. */
    private val tailCutters = listOf(
        "sms block", "call 1", "call 18", "call us", "to dispute", "for dispute", "not you", "if not done",
        "if not you", "if not u", "if you have not", "avl bal", "avl. bal", "available bal", "avl lmt", "available limit", "-icici", "- icici"
    )

    private const val AMOUNT = """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountRegex = Regex(AMOUNT, RegexOption.IGNORE_CASE)
    /** SBI-style "debited by 60.0" with no currency sign. */
    private val bareDebitAmount = Regex("""(?:debited\s+(?:by|for|with)|debit of|sent|paid)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""", RegexOption.IGNORE_CASE)

    private val utrRegex = Regex(
        """(?:utr|rrn|upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*no)?|upi|txn\s*id|transaction\s*id)[\s.:#-]*(?:no[\s.:#-]*)?([0-9]{10,16})""",
        RegexOption.IGNORE_CASE
    )

    // Payee patterns, most specific first.
    private val payeePatterns = listOf(
        // ICICI: "...; COFFEE DAY credited. UPI:..."
        Regex(""";\s*([A-Za-z0-9 .&'@_-]{2,40}?)\s+credited""", RegexOption.IGNORE_CASE),
        // SBI/Axis: "Info: UPI/P2M/123456/SWIGGY" or "UPI/DR/1234/NAME/BANK"
        Regex("""upi/(?:p2[ma]|dr|cr)?/?[0-9]+/([A-Za-z0-9 .&'_-]{2,40})""", RegexOption.IGNORE_CASE),
        // "to VPA swiggy@icici" / "trf to NAME" / "To NAME On"
        Regex("""\b(?:to|trf to|transfer to|paid to)\s+(?:vpa\s+)?([A-Za-z0-9 .&'@_-]{2,40}?)(?=\s+(?:on|via|ref\w*|utr|upi|from|using|at|thru|through)\b|[.,;\n(]|$)""", RegexOption.IGNORE_CASE),
        // Cards: "spent ... at AMAZON on" / "at MERCHANT."
        Regex("""\bat\s+([A-Za-z0-9 .&'_*-]{2,40}?)(?=\s+(?:on|via|ref|using|txn)\b|[.,;\n(]|$)""", RegexOption.IGNORE_CASE),
    )

    fun parse(sender: String, body: String): ParsedUpiTransaction? {
        val lower = body.lowercase(Locale.US)
        if (debitKeywords.none { it in lower }) return null
        val isMoneyMsg = moneyIndicators.any { it in lower } || sender.lowercase(Locale.US).contains("upi")
        if (!isMoneyMsg) return null
        if (ignoreKeywords.any { it in lower }) return null

        val core = cutTail(body)
        val amount = debitAmount(core) ?: debitAmount(body)
            ?: bareDebitAmount.find(core)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null
        if (amount <= 0.0 || amount > 10_000_000) return null

        val payee = findPayee(core)
        val utr = utrRegex.find(body)?.groupValues?.getOrNull(1)
        val channel = detectChannel(lower)
        val bank = detectBank(sender, lower)
        val merchant = payee ?: fallbackMerchant(channel, bank)

        return ParsedUpiTransaction(
            merchant = merchant,
            amount = amount,
            currency = "INR",
            category = inferCategory(merchant, lower),
            bankOrApp = "$channel · $bank",
            utrRef = utr,
            rawSnippet = body.trim().take(200),
            channel = channel,
            account = accounts(core).firstOrNull(),
            toAccount = accounts(core).drop(1).firstOrNull { it != accounts(core).firstOrNull() }
        )
    }

    private val creditKeywords = listOf("credited", "received", "deposited")
    private val notRealCredit = listOf(
        "otp", "one time password", "verification code", "will be credited", "to be credited", "cashback", "offer", "win ",
        "request", "loan", "failed", "declined", "unsuccessful"
    )
    private val debitWords = listOf("debited", "sent ", "paid ", "spent", "withdrawn", "purchase", "transferred to")

    /**
     * Reads a "credited" message: amount, UPI ref and the account it landed in. A debit alert that merely says
     * "NAME credited" is not a credit, so messages with debit wording are skipped.
     */
    fun parseCredit(sender: String, body: String): ParsedCredit? {
        val lower = body.lowercase(Locale.US)
        if (creditKeywords.none { it in lower }) return null
        if (debitWords.any { it in lower }) return null
        if (notRealCredit.any { it in lower }) return null
        if (moneyIndicators.none { it in lower } && !sender.lowercase(Locale.US).contains("upi")) return null
        val core = cutTail(body)
        val amount = debitAmount(core) ?: debitAmount(body) ?: return null
        if (amount <= 0.0 || amount > 10_000_000) return null
        return ParsedCredit(amount, utrRegex.find(body)?.groupValues?.getOrNull(1), accounts(core).firstOrNull())
    }

    private val accountMarked = Regex("""(?:a/c|acct?|account)\s*(?:no\.?)?\s*[:.]?\s*(?:x+|\*+)?\s*([0-9]{3,4})\b""", RegexOption.IGNORE_CASE)
    private val accountStarred = Regex("""(?<![A-Za-z0-9])(?:x+|\*+)\s*([0-9]{3,4})\b""", RegexOption.IGNORE_CASE)

    /** Last digits of every account mentioned, in the order they appear. */
    fun accounts(text: String): List<String> {
        val found = (accountMarked.findAll(text) + accountStarred.findAll(text))
            .sortedBy { it.range.first }
            .map { it.groupValues[1] }
            .toList()
        return found.distinct()
    }

    private fun cutTail(body: String): String {
        val lower = body.lowercase(Locale.US)
        val cut = tailCutters.mapNotNull { k -> lower.indexOf(k).takeIf { it > 15 } }.minOrNull()
        return if (cut != null) body.substring(0, cut) else body
    }

    /** First rupee amount that isn't a balance or limit. */
    private fun debitAmount(text: String): Double? {
        val lower = text.lowercase(Locale.US)
        return amountRegex.findAll(text).firstNotNullOfOrNull { m ->
            val before = lower.substring(maxOf(0, m.range.first - 18), m.range.first)
            if (listOf("bal", "balance", "limit", "lmt", "avl").any { it in before }) null
            else m.groupValues[1].replace(",", "").toDoubleOrNull()
        }
    }

    private fun findPayee(text: String): String? {
        for (re in payeePatterns) {
            val raw = re.find(text)?.groupValues?.getOrNull(1) ?: continue
            val cleaned = cleanPayee(raw) ?: continue
            return cleaned
        }
        return null
    }

    private fun cleanPayee(raw: String): String? {
        var s = raw.trim().trim('.', ',', '-', '*', ' ')
            .replace(Regex("\\s+"), " ")
        // Strip a VPA handle to its name part: swiggy.food@icici → swiggy.food
        if ('@' in s) s = s.substringBefore('@')
        val lower = s.lowercase(Locale.US)
        // Reject account numbers, phone numbers and generic words.
        if (s.length < 2) return null
        if (s.count { it.isDigit() } >= s.length - 1) return null
        if (Regex("""^(x+|\*+)?\s*[0-9x*]{3,}$""", RegexOption.IGNORE_CASE).matches(lower)) return null
        val junk = listOf("your", "a/c", "acct", "account", "card", "bank", "upi", "beneficiary", "you", "self")
        if (junk.any { lower == it || lower.startsWith("$it ") }) return null
        // SHOUTING NAMES → Title Case; mixed-case names are kept as the bank wrote them.
        if (s.none { it.isLowerCase() }) {
            s = s.split(' ').joinToString(" ") { w -> w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } }
        }
        return s.take(36)
    }

    private val atmRegex = Regex("""\batm\b""")

    private fun detectChannel(lower: String): String = when {
        atmRegex.containsMatchIn(lower) || "withdrawn" in lower -> "ATM"
        "upi" in lower || "vpa" in lower || "gpay" in lower || "phonepe" in lower || "bhim" in lower || "paytm" in lower -> "UPI"
        "card" in lower || Regex("""\bpos\b""").containsMatchIn(lower) -> "Card"
        "imps" in lower || "neft" in lower || "rtgs" in lower || "trf" in lower || "transfer" in lower -> "Bank transfer"
        else -> "Bank"
    }

    private fun fallbackMerchant(channel: String, bank: String) = when (channel) {
        "ATM" -> "Cash withdrawal"
        "Card" -> "Card payment"
        "Bank transfer" -> "Bank transfer"
        else -> "$bank payment"
    }

    private fun inferCategory(merchant: String, lowerBody: String): String {
        val m = merchant.lowercase(Locale.US)
        fun has(words: List<String>) = words.any { w -> Regex("\\b${Regex.escape(w)}").containsMatchIn(m) } ||
            words.any { w -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(lowerBody) }
        return when {
            atmRegex.containsMatchIn(lowerBody) || "withdrawn" in lowerBody -> "Cash"
            has(listOf("swiggy", "zomato", "coffee", "coffe", "cafe", "blinkit", "zepto", "bigbasket", "biryani", "restaurant", "bakery", "dmart", "instamart", "starbucks", "chai", "dosa", "food", "eats", "pizza", "kitchen", "hotel")) -> "Food"
            has(listOf("uber", "ola", "rapido", "namma yatri", "metro", "irctc", "indigo", "petrol", "fuel", "shell", "hpcl", "bpcl", "iocl", "fastag", "redbus")) -> "Transport"
            has(listOf("amazon", "flipkart", "myntra", "nykaa", "ajio", "decathlon", "croma", "mall", "meesho", "store", "mart", "retail", "fashion")) -> "Shopping"
            has(listOf("bescom", "airtel", "jio", "vodafone", "act fibernet", "electricity", "broadband", "recharge", "bill", "bbps", "gas", "water", "insurance", "lic")) -> "Bills"
            has(listOf("apollo", "pharmacy", "medplus", "hospital", "clinic", "1mg", "pharmeasy", "netmeds", "diagnostic", "lab")) -> "Health"
            has(listOf("netflix", "spotify", "hotstar", "prime video", "bookmyshow", "pvr", "inox", "youtube")) -> "Entertainment"
            else -> "Other"
        }
    }

    private fun detectBank(sender: String, lowerBody: String): String {
        val s = "${sender.lowercase(Locale.US)} $lowerBody"
        return when {
            "hdfc" in s -> "HDFC Bank"
            "icici" in s -> "ICICI Bank"
            "sbi" in s || "state bank" in s -> "SBI"
            "axis" in s -> "Axis Bank"
            "kotak" in s -> "Kotak"
            "yes bank" in s || "yesbnk" in s -> "Yes Bank"
            "idfc" in s -> "IDFC First"
            "indusind" in s -> "IndusInd"
            "pnb" in s || "punjab national" in s -> "PNB"
            "canara" in s -> "Canara Bank"
            "bob" in s || "bank of baroda" in s -> "Bank of Baroda"
            "federal" in s -> "Federal Bank"
            "gpay" in s || "google pay" in s -> "Google Pay"
            "phonepe" in s -> "PhonePe"
            "paytm" in s -> "Paytm"
            "amazon pay" in s -> "Amazon Pay"
            else -> sender.ifBlank { "Bank" }
        }
    }
}
