package com.keepsakeai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.data.preferences.NudgePrefs
import com.keepsakeai.app.data.preferences.ReminderStore
import com.keepsakeai.app.data.preferences.TodoStore
import com.keepsakeai.app.notify.Notifier
import com.keepsakeai.app.ui.brief.buildBrief
import com.keepsakeai.app.ui.spend.startOfDay
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 8am morning brief and 9:30pm "how was your day?" journal nudge. */
class DailyNudgeWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MindSpaceApp
        val nudges = NudgePrefs(app)
        when (inputData.getString("kind")) {
            "brief" -> if (nudges.morningBrief) {
                val dao = app.database.dao()
                val brief = buildBrief(
                    ReminderStore(app).all(), TodoStore(app).all(), dao.getRecentMemories(500),
                    dao.observeAllExpenses().first(), dao.countPendingUpi(), MoneyPrefs(app).budgets, nudges.viewed,
                    events = com.keepsakeai.app.calendar.CalendarReader.events(app, startOfDay(System.currentTimeMillis()), startOfDay(System.currentTimeMillis()) + 24L * 60 * 60 * 1000)
                )
                Notifier.show(app, Notifier.REVIEW, 7101, "Your morning brief", brief.summary(), openOverlay = "brief")
            }
            "journal" -> if (nudges.eveningJournal) {
                val today = startOfDay(System.currentTimeMillis())
                val journaled = app.database.dao().getRecentMemories(50)
                    .any { it.createdAt >= today && it.source == CaptureSource.VOICE_MEMO && it.rawContent.startsWith("Voice journal") }
                if (!journaled) {
                    Notifier.show(app, Notifier.REMINDERS, 7102, "How was your day?", "Talk for a minute. trove will keep what matters.", openOverlay = "journal")
                }
            }
        }
        return Result.success()
    }

    companion object {
        private fun delayUntil(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }

        private fun schedule(context: Context, name: String, kind: String, hour: Int, minute: Int) {
            val req = PeriodicWorkRequestBuilder<DailyNudgeWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("kind" to kind))
                .build()
            // UPDATE keeps the cadence if already scheduled; scheduling is idempotent on each launch.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun scheduleAll(context: Context) {
            schedule(context, "morning_brief", "brief", 8, 0)
            schedule(context, "evening_journal", "journal", 21, 30)
        }
    }
}
