package com.keepsakeai.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Play Store-friendly live UPI detector.
 * Listens to incoming notifications from SMS apps and UPI apps (GPay, PhonePe, Paytm, Bank apps)
 * and queues detected transactions into the Preview & Approve inbox without auto-posting them.
 */
class UpiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val supportedPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                        // PhonePe
        "net.one97.paytm",                        // Paytm
        "in.org.npci.upiapp"                      // BHIM
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg !in supportedPackages && !pkg.contains("bank", ignoreCase = true)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val parsed = UpiMessageParser.parse(title, text) ?: return
        val app = application as? MindSpaceApp ?: return

        scope.launch {
            val dao = app.database.dao()
            // Deduplicate by UTR or identical recent snippet
            if (parsed.utrRef != null && dao.countPendingByUtr(parsed.utrRef) > 0) return@launch

            dao.insertPendingUpi(
                PendingUpiTransactionEntity(
                    merchant = parsed.merchant,
                    amount = parsed.amount,
                    currency = parsed.currency,
                    suggestedCategory = parsed.category,
                    bankOrApp = parsed.bankOrApp,
                    utrRef = parsed.utrRef,
                    rawMessage = parsed.rawSnippet
                )
            )
        }
    }
}
