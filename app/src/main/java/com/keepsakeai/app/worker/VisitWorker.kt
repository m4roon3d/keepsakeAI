package com.keepsakeai.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.data.local.MindSpaceDao
import com.keepsakeai.app.data.local.PlaceVisitEntity
import com.keepsakeai.app.places.LocationPrefs
import com.keepsakeai.app.places.OsmPlaces
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
                dao.updateVisit(nameStay(prefs, step.visit))
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
        nameOlderStays(prefs, dao)
        return Result.success()
    }

    /**
     * Gives a stay of 10+ minutes a name: the phone's street/area first, then the venue (restaurant, shop, landmark,
     * building...) from OpenStreetMap when one is mapped within [VENUE_RADIUS_M]. Names the user typed or confirmed are left alone.
     */
    private suspend fun nameStay(prefs: LocationPrefs, v: PlaceVisitEntity): PlaceVisitEntity {
        if (!VisitTracker.isVisit(v) || v.confirmed) return v
        var out = v
        if (out.name == null) {
            DeviceContext.placeNameAt(applicationContext, out.lat, out.lng)?.let { out = out.copy(name = it) }
        }
        if (!prefs.venueLookup || prefs.isVenueChecked(out.id)) return out
        // null means the lookup failed (offline, busy): try again on the next check instead of giving up on this stay.
        val venues = OsmPlaces.nearbyOrNull(out.lat, out.lng, VENUE_RADIUS_M, 1) ?: return out
        prefs.markVenueChecked(out.id)
        venues.firstOrNull()?.let { out = out.copy(name = it.name) }
        return out
    }

    /** Names stays recorded before venue lookup existed, a few per check so the free service is not hammered. */
    private suspend fun nameOlderStays(prefs: LocationPrefs, dao: MindSpaceDao) {
        if (!prefs.venueLookup) return
        runCatching {
            dao.allVisits()
                .filter { VisitTracker.isVisit(it) && !it.confirmed && !prefs.isVenueChecked(it.id) }
                .take(BACKFILL_PER_RUN)
                .forEach { v ->
                    val named = nameStay(prefs, v)
                    if (named != v) dao.updateVisit(named)
                    kotlinx.coroutines.delay(1_500)
                }
        }
    }

    companion object {
        /** Stays are logged from a phone fix that can be off by a few dozen metres, so look a little wider than a photo. */
        const val VENUE_RADIUS_M = 75
        private const val BACKFILL_PER_RUN = 8
        private const val NAME = "places_visited"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VisitWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
