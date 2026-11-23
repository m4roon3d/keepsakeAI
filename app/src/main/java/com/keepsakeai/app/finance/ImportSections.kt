package com.keepsakeai.app.finance

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity

/** Where an imported email or SMS belongs. Keeps UPI, bank, mutual fund and stock or crypto mail apart. */
enum class ImportSection(val label: String) {
    UPI("UPI"),
    BANK("Bank"),
    MF("Mutual funds"),
    INVESTMENTS("Stocks & crypto"),
    OTHER("Other mail"),
}

/** Pure sorting and visibility rules for imported memories. Unit-tested. */
object ImportSections {
    const val FRESH_MS = 48L * 60 * 60 * 1000

    private val mf = Regex("""mutual fund|\bfolio\b|\bnav\b|\bsip\b|units? allot|\bcams\b|kfin|karvy|redemption|\bamc\b|fund house|\belss\b""")
    private val invest = Regex("""zerodha|groww|upstox|angel ?one|icici ?direct|\bdemat\b|\bcdsl\b|\bnsdl\b|contract note|holding statement|coinbase|wazirx|coindcx|binance|bitcoin|crypto|stock ?broking|stock market|\bequity\b|\bipo\b""")
    private val upi = Regex("""\bupi\b|\bvpa\b|gpay|google pay|phonepe|paytm|\bbhim\b""")
    private val bank = Regex("""\bbank\b|a/c|\bacct\b|account statement|bank statement|credit card|debit card|card statement|statement of account|\bneft\b|\bimps\b|\brtgs\b|net ?banking|\bemi\b""")

    /** Memories that came in through a mailbox or the SMS listener rather than being captured by hand. */
    fun isImport(m: MemoryEntity) = m.source == CaptureSource.EMAIL || m.source == CaptureSource.UPI_MESSAGE

    fun of(m: MemoryEntity): ImportSection? {
        if (!isImport(m)) return null
        if (m.source == CaptureSource.UPI_MESSAGE) return ImportSection.UPI
        val text = "${m.title} ${m.tagsCsv} ${m.collectionName} ${m.rawContent.take(700)}".lowercase()
        return when {
            mf.containsMatchIn(text) -> ImportSection.MF
            invest.containsMatchIn(text) -> ImportSection.INVESTMENTS
            upi.containsMatchIn(text) -> ImportSection.UPI
            bank.containsMatchIn(text) -> ImportSection.BANK
            else -> ImportSection.OTHER
        }
    }

    /**
     * Whether a memory gets a card in the home feed. Things the user captured themselves always do. Imports only
     * when they are new, or relevant (an upcoming reminder or an open to-do points at them). The rest stay
     * searchable and browsable under Imports.
     */
    fun showInFeed(m: MemoryEntity, now: Long, relevantIds: Set<Long>): Boolean =
        !isImport(m) || now - m.createdAt <= FRESH_MS || m.id in relevantIds
}
