package com.keepsakeai.app.places

import com.keepsakeai.app.data.local.PlaceVisitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Pilot may see of your stays: day, time range and place name for the last two weeks. Never coordinates. */
object PlacesPrompt {
    const val DAYS = 14
    private const val MAX_LINES = 60

    fun block(visits: List<PlaceVisitEntity>, enabled: Boolean, now: Long = System.currentTimeMillis()): String {
        if (!enabled) return "Places visited: not switched on."
        val since = now - DAYS * 86_400_000L
        val recent = visits.filter { VisitTracker.isVisit(it) && it.arrivedAt >= since }
            .sortedByDescending { it.arrivedAt }
            .take(MAX_LINES)
        if (recent.isEmpty()) return "Places visited (last $DAYS days): nothing recorded yet."
        val day = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        return "Places visited (last $DAYS days, newest first):\n" + recent.joinToString("\n") { v ->
            "- ${day.format(Date(v.arrivedAt))}, ${time.format(Date(v.arrivedAt))}–${time.format(Date(v.lastSeenAt))}: ${v.name ?: "an unnamed place"}"
        }
    }
}
