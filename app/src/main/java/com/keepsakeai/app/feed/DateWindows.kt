package com.keepsakeai.app.feed

import java.util.Calendar
import java.util.TimeZone

/** A stretch of time the home feed can be narrowed to. "This week" is the last 7 days including today. */
enum class DateWindow(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), WEEK("This week") }

/** Pure helpers for "today", "yesterday" and "this week" in the feed and in search. Unit-tested. */
object DateWindows {
    private const val DAY = 24L * 60 * 60 * 1000

    private val yesterday = Regex("""\byesterday\b""")
    private val today = Regex("""\btoday\b""")
    private val week = Regex("""\b(?:this|last|past|a) week\b|\bweek\b""")

    private fun startOfDay(now: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Start (inclusive) and end (exclusive) of the window, in the phone's time zone. */
    fun range(w: DateWindow, now: Long, zone: TimeZone = TimeZone.getDefault()): LongRange {
        val start = startOfDay(now, zone)
        return when (w) {
            DateWindow.TODAY -> start until start + DAY
            DateWindow.YESTERDAY -> start - DAY until start
            DateWindow.WEEK -> start - 6 * DAY until start + DAY
        }
    }

    /** The window a search is asking about, and what is left of the query once the date words are removed. */
    fun detect(query: String): Pair<DateWindow?, String> {
        val q = query.lowercase()
        val found = listOf(DateWindow.YESTERDAY to yesterday, DateWindow.TODAY to today, DateWindow.WEEK to week)
            .mapNotNull { (w, re) -> re.find(q)?.let { Triple(w, re, it.range.first) } }
            .minByOrNull { it.third } ?: return null to query.trim()
        val rest = found.second.replace(q, " ").replace(Regex("""\b(?:from|on|in|during|of|the|my)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        return found.first to rest
    }
}
