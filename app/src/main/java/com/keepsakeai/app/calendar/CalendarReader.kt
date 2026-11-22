package com.keepsakeai.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One occurrence of a calendar event; all-day events are shifted to local midnight. */
data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val calendar: String = "",
) {
    fun timeLabel(): String =
        if (allDay) "all day"
        else SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(begin)).lowercase()
}

/** Reads the phone's calendars (Google, Samsung, Outlook app…) through Android's calendar provider. Read-only. */
object CalendarReader {

    private const val DAY = 24L * 60 * 60 * 1000

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(context: Context, from: Long, to: Long): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from - DAY) // all-day events are stored in UTC, widen and trim below
            ContentUris.appendId(it, to + DAY)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    if (c.getInt(8) == CalendarContract.Events.STATUS_CANCELED) continue
                    val allDay = c.getInt(4) == 1
                    val begin = if (allDay) utcMidnightToLocal(c.getLong(2)) else c.getLong(2)
                    val end = if (allDay) utcMidnightToLocal(c.getLong(3)) else c.getLong(3)
                    if (end <= from || begin >= to) continue
                    out += CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.trim().orEmpty().ifBlank { "(no title)" },
                        begin = begin, end = end, allDay = allDay,
                        location = c.getString(5)?.trim().orEmpty(),
                        calendar = c.getString(6).orEmpty()
                    )
                }
            }
        }
        return out.distinctBy { "${it.id}@${it.begin}" }.sortedBy { it.begin }
    }

    private fun utcMidnightToLocal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** What Pilot sees: the last 2 days and the next 3 weeks. */
    fun promptBlock(context: Context, now: Long = System.currentTimeMillis()): String {
        if (!hasPermission(context)) return "Phone calendar: not connected."
        val list = events(context, now - 2 * DAY, now + 21 * DAY)
        if (list.isEmpty()) return "Phone calendar: nothing from 2 days ago to 3 weeks ahead."
        return "Phone calendar (2 days ago to 3 weeks ahead):\n" + formatForPrompt(list.take(60))
    }

    fun formatForPrompt(list: List<CalEvent>): String {
        val day = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        return list.joinToString("\n") { e ->
            val whenText = if (e.allDay) {
                val days = ((e.end - e.begin + DAY / 2) / DAY).toInt()
                day.format(Date(e.begin)) + if (days > 1) " to " + day.format(Date(e.end - 1)) + " (all day)" else " (all day)"
            } else "${day.format(Date(e.begin))}, ${time.format(Date(e.begin))}–${time.format(Date(e.end))}"
            "- $whenText: ${e.title}" + (if (e.location.isNotBlank()) " @ ${e.location}" else "")
        }
    }

    fun openIntent(id: Long): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
}
