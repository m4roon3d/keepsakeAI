package com.keepsakeai.app.places

import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PlaceVisitEntity

/** One pin on the map: something you kept, or somewhere you stayed. */
data class MapPoint(
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val memoryId: Long? = null
) {
    enum class Kind { MEMORY, STAY }
}

object MapPoints {
    fun memories(list: List<MemoryEntity>): List<MapPoint> = list.mapNotNull { m ->
        val at = if (m.lat != null && m.lng != null) m.lat to m.lng else NearHere.parseCoords(m.rawContent) ?: return@mapNotNull null
        MapPoint("m${m.id}", MapPoint.Kind.MEMORY, m.title, m.collectionName, at.first, at.second, m.id)
    }

    fun stays(list: List<PlaceVisitEntity>): List<MapPoint> = list.filter { VisitTracker.isVisit(it) }.map { v ->
        val mins = ((v.lastSeenAt - v.arrivedAt) / 60_000L).toInt()
        MapPoint("v${v.id}", MapPoint.Kind.STAY, v.name ?: "Unnamed place", "stayed ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}", v.lat, v.lng)
    }

    /** south, west, north, east */
    fun bounds(points: List<MapPoint>): DoubleArray? {
        if (points.isEmpty()) return null
        return doubleArrayOf(points.minOf { it.lat }, points.minOf { it.lng }, points.maxOf { it.lat }, points.maxOf { it.lng })
    }
}
