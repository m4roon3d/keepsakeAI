package com.keepsakeai.app.capture

import android.content.Context
import android.provider.Telephony
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity

/**
 * Optional historical SMS inbox scanner for personal builds where READ_SMS is granted.
 * Scans recent inbox messages and returns parsed UPI transactions for Preview & Approve.
 */
object UpiSmsInboxScanner {

    fun scanRecentInbox(context: Context, maxMessages: Int = 100): List<PendingUpiTransactionEntity> {
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
                null,
                null,
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
        return results
    }
}
