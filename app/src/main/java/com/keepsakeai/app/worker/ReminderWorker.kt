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
        val hasTime = inputData.getBoolean("hasTime", true)
        val kind = inputData.getString("kind") ?: "event"
        val today = sameDay(at, System.currentTimeMillis())
        val day = if (today) "Today" else SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(at))
        val time = if (hasTime) " at " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(at)).lowercase() else ""
        val text = when (kind) {
            "due" -> "Due ${day.lowercase()}$time"
            "deadline" -> "Deadline ${day.lowercase()}$time"
            else -> "$day$time"
        }
        Notifier.show(applicationContext, Notifier.REMINDERS, (inputData.getLong("id", 0L) + inputData.getInt("n", 0)).toInt(), title, text, openTab = "home")
        return Result.success()
    }

    companion object {
        private fun sameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }

        private fun at(base: Long, daysBefore: Int, hour: Int): Long = Calendar.getInstance().apply {
            timeInMillis = base
            add(Calendar.DAY_OF_MONTH, -daysBefore)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /**
         * Events: the evening before (7pm) plus an hour before the start — or 9am on the day
         * when only a date is known. Bills and deadlines: 9am the day before and 9am on the day.
         */
        fun notifyTimesFor(r: KeepReminder, now: Long = System.currentTimeMillis()): List<Long> {
            val times = when (r.kind) {
                "event" -> listOf(at(r.atMillis, 1, 19), if (r.hasTime) r.atMillis - TimeUnit.HOURS.toMillis(1) else at(r.atMillis, 0, 9))
                else -> listOf(at(r.atMillis, 1, 9), at(r.atMillis, 0, 9))
            }.filter { it > now + TimeUnit.MINUTES.toMillis(1) }.distinct().sorted()
            return times.ifEmpty { listOf(now + TimeUnit.MINUTES.toMillis(1)) }
        }

        /** Schedules every nudge for [r]; returns their times. */
        fun schedule(context: Context, r: KeepReminder): List<Long> {
            cancel(context, r.id)
            val times = notifyTimesFor(r)
            times.forEachIndexed { i, t ->
                val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(t - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("title" to r.title, "at" to r.atMillis, "id" to r.id, "n" to i, "hasTime" to r.hasTime, "kind" to r.kind))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork("reminder_${r.id}_$i", ExistingWorkPolicy.REPLACE, req)
            }
            return times
        }

        fun cancel(context: Context, id: Long) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork("reminder_$id")
            (0..2).forEach { wm.cancelUniqueWork("reminder_${id}_$it") }
        }
    }
}
