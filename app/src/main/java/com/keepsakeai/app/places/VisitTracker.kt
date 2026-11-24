package com.keepsakeai.app.places

import com.keepsakeai.app.data.local.PlaceVisitEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Decides, from one location reading at a time, whether the phone is still at the same place. */
object VisitTracker {
    const val RADIUS_M = 100.0
    const val MIN_STAY_MS = 10 * 60_000L
    /** A reading this long after the last one is treated as a new stay (the phone was off or elsewhere meanwhile). */
    const val MAX_GAP_MS = 2 * 60 * 60_000L
    const val EXCLUDE_RADIUS_M = 150.0

    sealed interface Step {
        /** Still at the open stay: move its last-seen time forward. */
        data class Extend(val visit: PlaceVisitEntity) : Step
        /** Somewhere new: close [closing] (may be null) and open a stay at the reading. */
        data class Start(val closing: PlaceVisitEntity?) : Step
    }

    fun step(open: PlaceVisitEntity?, lat: Double, lng: Double, now: Long): Step {
        if (open == null) return Step.Start(null)
        val near = distanceMeters(open.lat, open.lng, lat, lng) <= RADIUS_M
        val fresh = now - open.lastSeenAt <= MAX_GAP_MS
        return if (near && fresh) Step.Extend(open.copy(lastSeenAt = now)) else Step.Start(open)
    }

    fun isVisit(v: PlaceVisitEntity): Boolean = v.lastSeenAt - v.arrivedAt >= MIN_STAY_MS

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
