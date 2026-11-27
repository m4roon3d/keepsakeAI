package com.keepsakeai.app.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.capture.CapturePipeline
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.email.EmailFilter
import com.keepsakeai.app.email.EmailStore
import com.keepsakeai.app.email.MailAuthException
import com.keepsakeai.app.email.MailReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Reads new receipts, orders, tickets, bills and renewals from connected mailboxes every few hours. */
class EmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = lock.withLock { sync() }

    private suspend fun sync(): Result {
        val app = applicationContext as MindSpaceApp
        val store = EmailStore(app)
        val accounts = store.all()
        if (accounts.isEmpty()) return Result.success()
        if (app.apiKeyStore.geminiKey.isBlank()) {
            accounts.forEach { a -> store.update(a.id) { it.copy(lastError = "Add a Gemini key in Settings so trove can read emails.") } }
            return Result.success()
        }
        val pipeline = CapturePipeline(app)
        var anyKept = false

        for (account in accounts) {
            val password = app.apiKeyStore.emailPassword(account.id)
            if (password.isBlank()) continue
            val firstRun = account.lastSyncAt == 0L
            val since = if (firstRun) System.currentTimeMillis() - FIRST_WINDOW else account.lastSyncAt - OVERLAP
            val items = try {
                withContext(Dispatchers.IO) {
                    MailReader.fetch(account.host, account.port, account.address, password, since, store.seen(account.id), if (firstRun) 40 else 25)
                }
            } catch (e: MailAuthException) {
                store.update(account.id) { it.copy(lastError = e.message) }
                continue
            } catch (e: Exception) {
                store.update(account.id) { it.copy(lastError = "Couldn't reach ${account.provider.label}: ${e.message?.take(80) ?: "network error"}") }
                continue
            }

            var kept = 0
            var stoppedEarly = false
            for (item in items) {
                val text = buildString {
                    append("Email from ${EmailFilter.senderName(item.from)} · received ")
                    append(SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(item.date)))
                    append("\nSubject: ${item.subject}\n\n${item.body}")
                }
                val saved = runCatching {
                    pipeline.save(text, CaptureSource.EMAIL, at = item.date, hint = EMAIL_HINT, skipIfNotWorthKeeping = true)
                }.getOrNull()
                if (saved?.aiFailed == true) { stoppedEarly = true; break }  // Gemini busy: try the rest next time
                store.markSeen(account.id, listOf(item.key))
                if (saved?.memoryId != null) kept++
                delay(2_500) // stay under Gemini's free-tier rate limit
            }
            if (kept > 0) anyKept = true
            store.update(account.id) {
                it.copy(
                    lastSyncAt = if (stoppedEarly) it.lastSyncAt else System.currentTimeMillis(),
                    kept = it.kept + kept,
                    lastError = if (stoppedEarly) "Gemini was busy, the rest will be read on the next sync." else null
                )
            }
        }

        if (anyKept) {
            runCatching { com.keepsakeai.app.widget.TroveWidgets.updateAll(app) }
            runCatching { com.keepsakeai.app.notify.MoneyWatch.check(app) }
        }
        return Result.success()
    }

    companion object {
        const val TAG = "email_sync"
        private val lock = Mutex()
        private const val PERIODIC = "email_sync_periodic"
        private const val NOW = "email_sync_now"
        private const val DAY = 24L * 60 * 60 * 1000
        private const val FIRST_WINDOW = 30 * DAY
        private const val OVERLAP = 2 * DAY

        private val EMAIL_HINT = """
            This capture is an email from the user's inbox. Add a key "keep": false when it is marketing, a newsletter, a promotion,
            an OTP or security notice, or holds nothing the user would want to remember later; otherwise "keep": true.
            Only fill "expense" when the email confirms money the user actually paid or was charged (receipt, invoice, order payment,
            card or UPI debit, subscription renewal charge). Never for offers, quotes, refunds, balances or amounts still due,
            and never for money the user moved between their own accounts (transfers, credit card bill payments, mutual fund or stock purchases).
            Use the plain brand as merchant, e.g. "Netflix", "Amazon", "IRCTC", "Airtel", so repeat charges match.
            Bills and card statements with a due date: add a todo with that due date and a "due" reminder.
            Tickets and bookings: add an "event" reminder at the travel or show time, with the venue or PNR in the summary.
            Collection: receipts and orders -> "Receipts", trips and travel bookings -> "Travel", event tickets -> "Events".
        """.trimIndent()

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 6 hours while at least one mailbox is connected. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (EmailStore(context).all().isEmpty()) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EmailSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints).addTag(TAG).build()
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EmailSyncWorker>().setConstraints(constraints).addTag(TAG).build()
            )
        }
    }
}
