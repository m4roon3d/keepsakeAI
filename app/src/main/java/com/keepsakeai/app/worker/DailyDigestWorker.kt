package com.keepsakeai.app.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
                    headline = "keepsake Suggest Daily Brief",
                    body = "${recent.size} recent keepsakes across ${recent.map { it.collectionName }.distinct().size} Smart Collections.",
                    actionLabel = "Open Hub"
                )
            )
        }
        runCatching { MindSpaceWidget().updateAll(app) }
        return Result.success()
    }
}
