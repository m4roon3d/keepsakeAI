package com.keepsakeai.app.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UpcomingCalendarEvent(
    val title: String,
    val startTimeMillis: Long,
    val location: String
)

data class TravelCardHighlight(
    val carrier: String,
    val pnrOrFlight: String,
    val summary: String
)

object CalendarAndTravelAggregator {

    private val pnrRegex = Regex("""(?:pnr|booking\s*id)[:\s#-]*([A-Z0-9]{6,10})""", RegexOption.IGNORE_CASE)
    private val flightRegex = Regex("""\b(6E|AI|UK|SG|QP|IX)[-\s]?(\d{3,4})\b""")

    fun getUpcomingCalendarEvents(context: Context, nextHours: Long = 48L): List<UpcomingCalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val end = now + nextHours * 3600_000L
        val events = mutableListOf<UpcomingCalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val tIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val sIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val lIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                while (cursor.moveToNext() && events.size < 5) {
                    val title = if (tIdx >= 0) cursor.getString(tIdx).orEmpty() else ""
                    val start = if (sIdx >= 0) cursor.getLong(sIdx) else 0L
                    val loc = if (lIdx >= 0) cursor.getString(lIdx).orEmpty() else ""
                    if (title.isNotBlank()) events.add(UpcomingCalendarEvent(title, start, loc))
                }
            }
        }
        return events
    }

    fun extractTravelHighlight(text: String): TravelCardHighlight? {
        val lower = text.lowercase(Locale.US)
        val isTravel = listOf("irctc", "makemytrip", "pnr", "indigo", "air india", "vistara", "akasa", "train", "boarding").any { it in lower }
        if (!isTravel) return null
        val pnr = pnrRegex.find(text)?.groupValues?.getOrNull(1)
        val flight = flightRegex.find(text)?.value
        val code = pnr ?: flight ?: return null
        val carrier = when {
            "irctc" in lower || "train" in lower -> "IRCTC / Train"
            "makemytrip" in lower -> "MakeMyTrip"
            "indigo" in lower || "6e" in lower -> "IndiGo"
            "air india" in lower -> "Air India"
            else -> "Travel Alert"
        }
        return TravelCardHighlight(carrier = carrier, pnrOrFlight = code, summary = text.take(120))
    }

    fun formatEventTime(millis: Long): String =
        SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(millis))
}
