package com.keepsakeai.app.places

import android.content.Context
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.data.local.MindSpaceDao
import com.keepsakeai.app.data.local.PlaceVisitEntity
import kotlinx.coroutines.delay

/** Gives stays a name: the venue from OpenStreetMap when one is mapped nearby, else the phone's street or area. */
object VisitNamer {
    /** Stays are logged from a phone fix that can be off by a few dozen metres, so look a little wider than a photo. */
    const val VENUE_RADIUS_M = 75
    private const val BACKFILL_PER_RUN = 8

    /**
     * Names a stay of 10+ minutes: the phone's street/area first, then the venue (restaurant, shop, landmark, building...)
     * within [VENUE_RADIUS_M]. Names the user typed or confirmed are left alone.
     */
    suspend fun nameStay(context: Context, prefs: LocationPrefs, v: PlaceVisitEntity): PlaceVisitEntity {
        if (!VisitTracker.isVisit(v) || v.confirmed) return v
        var out = v
        if (out.name == null) {
            DeviceContext.placeNameAt(context, out.lat, out.lng)?.let { out = out.copy(name = it) }
        }
        if (!prefs.venueLookup || prefs.isVenueChecked(out.id)) return out
        // null means no server answered (offline, busy): try again next time instead of giving up on this stay.
        val venues = OsmPlaces.nearbyOrNull(out.lat, out.lng, VENUE_RADIUS_M, 1) ?: return out
        prefs.markVenueChecked(out.id)
        venues.firstOrNull()?.let { out = out.copy(name = it.name) }
        return out
    }

    /** Names stays that have not had a venue lookup yet, a few at a time so the free service is not hammered. */
    suspend fun nameOlder(context: Context, prefs: LocationPrefs, dao: MindSpaceDao) {
        if (!prefs.venueLookup) return
        runCatching {
            dao.allVisits()
                .filter { VisitTracker.isVisit(it) && !it.confirmed && !prefs.isVenueChecked(it.id) }
                .take(BACKFILL_PER_RUN)
                .forEach { v ->
                    val named = nameStay(context, prefs, v)
                    if (named != v) dao.updateVisit(named)
                    delay(1_500)
                }
        }
    }
}
