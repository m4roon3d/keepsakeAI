package com.keepsakeai.app.places

import com.keepsakeai.app.data.local.MemoryEntity

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
            val d = VisitTracker.distanceMeters(lat, lng, at.first, at.second)
            if (d <= radius) Hit(m, d.toInt()) else null
        }.sortedBy { it.meters }.take(limit)
}
