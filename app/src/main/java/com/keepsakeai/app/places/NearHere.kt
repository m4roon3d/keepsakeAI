package com.keepsakeai.app.places

import com.keepsakeai.app.data.local.MemoryEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** "What did I save around here?": memories kept within a few hundred metres of a point. */
object NearHere {
    const val RADIUS_M = 300.0
    private val coords = Regex("""Location:\s*(-?\d{1,3}\.\d+),\s*(-?\d{1,3}\.\d+)""")

    /** Older memories carry their GPS only inside the saved text ("Location: 12.97160, 77.59460"). */
    fun parseCoords(raw: String): Pair<Double, Double>? {
        val m = coords.find(raw) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
    }

    data class Hit(val memory: MemoryEntity, val meters: Int)

    fun nearby(memories: List<MemoryEntity>, lat: Double, lng: Double, radius: Double = RADIUS_M, limit: Int = 30): List<Hit> =
        memories.mapNotNull { m ->
            val at = (m.lat ?: return@mapNotNull null) to (m.lng ?: return@mapNotNull null)
            val d = distanceMeters(lat, lng, at.first, at.second)
            if (d <= radius) Hit(m, d.toInt()) else null
        }.sortedBy { it.meters }.take(limit)

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
