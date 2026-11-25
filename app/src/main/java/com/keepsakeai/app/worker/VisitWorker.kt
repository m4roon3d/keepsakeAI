package com.keepsakeai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.places.LocationPrefs
import com.keepsakeai.app.places.VisitNamer
import com.keepsakeai.app.places.VisitTracker
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes (the shortest Android allows) takes one low-power reading and keeps a list of stays.
 * Nothing is read while paused, switched off, or standing at an excluded spot.
 */
class VisitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = LocationPrefs(applicationContext)
        val dao = (applicationContext as MindSpaceApp).database.dao()
        val now = System.currentTimeMillis()
        if (prefs.retentionDays > 0) dao.pruneVisits(now - prefs.retentionDays * 86_400_000L)
        if (!prefs.enabled || prefs.paused) return Result.success()

        val here = DeviceContext.currentLatLng(applicationContext) ?: return Result.success()
        val open = dao.latestVisit()
        if (prefs.isExcluded(here.first, here.second)) {
            // Close whatever was open so the gap is not stitched into one long stay.
            open?.let { dao.dropIfShort(it.id, VisitTracker.MIN_STAY_MS) }
            return Result.success()
        }

        when (val step = VisitTracker.step(open, here.first, here.second, now)) {
            is VisitTracker.Step.Extend -> {
                dao.updateVisit(VisitNamer.nameStay(applicationContext, prefs, step.visit))
            }
            is VisitTracker.Step.Start -> {
                step.closing?.let { dao.dropIfShort(it.id, VisitTracker.MIN_STAY_MS) }
                dao.insertVisit(
                    com.keepsakeai.app.data.local.PlaceVisitEntity(
                        lat = here.first, lng = here.second, arrivedAt = now, lastSeenAt = now
                    )
                )
            }
        }
        VisitNamer.nameOlder(applicationContext, prefs, dao)
        return Result.success()
    }

    companion object {
        private const val NAME = "places_visited"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VisitWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
