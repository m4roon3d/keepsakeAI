package com.keepsakeai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.notify.Notifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fires a reminder notification at the scheduled time. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.success()
        val at = inputData.getLong("at", 0L)
        val whenText = SimpleDateFormat("EEE d MMM, h:mm a", Locale.ENGLISH).format(Date(at))
        Notifier.show(applicationContext, Notifier.REMINDERS, inputData.getLong("id", 0L).toInt(), title, "Due $whenText", openTab = "home")
        return Result.success()
    }

    companion object {
        /** Bills and deadlines nudge a day ahead at 9am; events an hour before. */
        fun notifyTimeFor(r: KeepReminder, now: Long = System.currentTimeMillis()): Long {
            val t = if (r.kind == "event") r.atMillis - TimeUnit.HOURS.toMillis(1)
            else Calendar.getInstance().apply {
                timeInMillis = r.atMillis
                add(Calendar.DAY_OF_MONTH, -1)
                set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            return maxOf(t, now + TimeUnit.MINUTES.toMillis(1))
        }

        fun schedule(context: Context, r: KeepReminder): Long {
            val at = notifyTimeFor(r)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(at - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to r.title, "at" to r.atMillis, "id" to r.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("reminder_${r.id}", ExistingWorkPolicy.REPLACE, req)
            return at
        }

        fun cancel(context: Context, id: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("reminder_$id")
        }
    }
}
