package com.keepsakeai.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.finance.FinanceGuard
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

        val app = application as? MindSpaceApp ?: return
        val parsed = UpiMessageParser.parse(title, text)
        if (parsed == null) {
            // A credit is never saved, but it can reveal that a debit was a transfer between own accounts.
            val credit = UpiMessageParser.parseCredit(title, text) ?: return
            scope.launch { FinanceGuard(app.database.dao(), MoneyPrefs(app)).noteCredit(credit, System.currentTimeMillis()) }
            return
        }

        scope.launch {
            val dao = app.database.dao()
            val guard = FinanceGuard(dao, MoneyPrefs(app))
            val now = System.currentTimeMillis()
            // Same payment already saved or waiting (UTR, or same amount within minutes, from SMS or email).
            if (guard.isDuplicate(parsed.amount, parsed.utrRef, now)) return@launch
            guard.learnAccounts(listOf(parsed.account))
            val type = guard.classify(parsed, now)

            dao.insertPendingUpi(
                PendingUpiTransactionEntity(
                    merchant = parsed.merchant,
                    amount = parsed.amount,
                    currency = parsed.currency,
                    suggestedCategory = parsed.category,
                    bankOrApp = parsed.bankOrApp,
                    utrRef = parsed.utrRef,
                    rawMessage = parsed.rawSnippet,
                    type = type
                )
            )
        }
    }
}
