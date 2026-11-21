package com.keepsakeai.app.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.DigestSuggestionEntity
import com.keepsakeai.app.widget.MindSpaceWidget

class DailyDigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MindSpaceApp
        val dao = app.database.dao()
        val recent = dao.getRecentMemories(15)
        val calendarEvents = CalendarAndTravelAggregator.getUpcomingCalendarEvents(app)

        dao.clearSuggestions()

        calendarEvents.firstOrNull()?.let { nextEvent ->
            dao.insertSuggestion(
                DigestSuggestionEntity(
                    headline = "Next Up: ${nextEvent.title}",
                    body = "${CalendarAndTravelAggregator.formatEventTime(nextEvent.startTimeMillis)}${if (nextEvent.location.isNotBlank()) " • ${nextEvent.location}" else ""}",
                    actionLabel = "Prep in Pilot"
                )
            )
        }

        recent.firstNotNullOfOrNull { CalendarAndTravelAggregator.extractTravelHighlight(it.rawContent) }?.let { travel ->
            dao.insertSuggestion(
                DigestSuggestionEntity(
                    headline = "${travel.carrier}: ${travel.pnrOrFlight}",
                    body = travel.summary,
                    actionLabel = "View Travel Hub"
                )
            )
        }

        if (recent.isNotEmpty()) {
            dao.insertSuggestion(
                DigestSuggestionEntity(
                    headline = "trove Suggest Daily Brief",
                    body = "${recent.size} recent finds across ${recent.map { it.collectionName }.distinct().size} Smart Collections.",
                    actionLabel = "Open Hub"
                )
            )
        }
        runCatching { com.keepsakeai.app.notify.MoneyWatch.check(app) }
        runCatching { weeklyReviewNudge(app) }
        runCatching { MindSpaceWidget().updateAll(app) }
        return Result.success()
    }

    /** Sunday evening (or Monday morning if the phone slept): "your week is ready". */
    private suspend fun weeklyReviewNudge(app: MindSpaceApp) {
        val cal = java.util.Calendar.getInstance()
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val inWindow = (dow == java.util.Calendar.SUNDAY && hour >= 17) || (dow == java.util.Calendar.MONDAY && hour < 12)
        if (!inWindow) return
        val weekKey = java.text.SimpleDateFormat("YYYY-ww", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 12 * 60 * 60 * 1000))
        if (!com.keepsakeai.app.data.preferences.MoneyPrefs(app).firstTime("review:$weekKey")) return
        val expenses = app.database.dao().observeAllExpenses().first()
        val memories = app.database.dao().getRecentMemories(500)
        val w = com.keepsakeai.app.ui.review.computeWeek(expenses, memories)
        com.keepsakeai.app.notify.Notifier.show(
            app, com.keepsakeai.app.notify.Notifier.REVIEW, 7001,
            "Your week in trove",
            "₹" + String.format(java.util.Locale.US, "%,.0f", w.spent) + " spent · ${w.kept} kept. ${w.insight}",
            openOverlay = "review"
        )
    }
}
