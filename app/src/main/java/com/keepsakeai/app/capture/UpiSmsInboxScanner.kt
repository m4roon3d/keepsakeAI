package com.keepsakeai.app.capture

import android.content.Context
import android.provider.Telephony
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity

/**
 * Replays the SMS inbox (READ_SMS) so past UPI and bank debits can be imported.
 * Scans recent inbox messages and returns parsed UPI transactions for Preview & Approve.
 */
object UpiSmsInboxScanner {

    /** Reads inbox SMS from the last [days] days (newest first) and parses UPI/bank debits. Call off the main thread. */
    fun scanRecentInbox(context: Context, days: Int = 30, maxMessages: Int = 5000): List<PendingUpiTransactionEntity> {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val results = mutableListOf<PendingUpiTransactionEntity>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                var count = 0
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && count < maxMessages) {
                    count++
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val ts = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    // Banks and UPI apps send from short codes (e.g. AX-ICICIT); skip personal numbers.
                    if (Regex("""^\+?[0-9 ()-]{7,}$""").matches(sender.trim())) continue
                    val parsed = UpiMessageParser.parse(sender, body)
                    if (parsed != null) {
                        results.add(
                            PendingUpiTransactionEntity(
                                merchant = parsed.merchant,
                                amount = parsed.amount,
                                currency = parsed.currency,
                                suggestedCategory = parsed.category,
                                bankOrApp = parsed.bankOrApp,
                                utrRef = parsed.utrRef,
                                rawMessage = parsed.rawSnippet,
                                receivedAt = ts
                            )
                        )
                    }
                }
            }
        }
        return dedupe(results)
    }

    /**
     * Phones often keep the same SMS twice (dual SIM, RCS, OEM backups) and banks
     * sometimes send a debit alert twice. Collapse by UPI ref, else by same amount
     * within five minutes.
     */
    private fun dedupe(items: List<PendingUpiTransactionEntity>): List<PendingUpiTransactionEntity> {
        val out = mutableListOf<PendingUpiTransactionEntity>()
        val refs = HashSet<String>()
        for (it in items) {
            val ref = it.utrRef
            if (ref != null) {
                if (!refs.add(ref)) continue
            } else if (out.any { o -> o.utrRef == null && o.amount == it.amount && kotlin.math.abs(o.receivedAt - it.receivedAt) < 5 * 60_000 }) {
                continue
            }
            out.add(it)
        }
        return out
    }
}
