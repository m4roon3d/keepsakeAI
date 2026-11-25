package com.keepsakeai.app.expiry

import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.startOfDay
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone

/** Things that end: warranties, insurance, memberships, passports, coupons. Pure and unit-tested. */
object Expiries {
    const val KIND = "expiry"
    /** An expired item stays listed this long, so you can still claim or renew, then it goes. */
    const val EXPIRED_GRACE_DAYS = 30

    enum class Bucket(val label: String) { EXPIRED("recently ended"), SOON("next 30 days"), LATER("later") }

    fun isExpiry(r: KeepReminder) = r.kind == KIND && r.status != "dismissed"

    fun daysLeft(r: KeepReminder, now: Long): Int =
        Math.floorDiv(startOfDay(r.atMillis) - startOfDay(now), DAY).toInt()

    fun bucket(days: Int): Bucket = when {
        days < 0 -> Bucket.EXPIRED
        days <= 30 -> Bucket.SOON
        else -> Bucket.LATER
    }

    /** Active expiries, soonest first, without ones that ended more than [EXPIRED_GRACE_DAYS] ago. */
    fun list(reminders: List<KeepReminder>, now: Long): List<KeepReminder> =
        reminders.filter { isExpiry(it) && daysLeft(it, now) >= -EXPIRED_GRACE_DAYS }.sortedBy { it.atMillis }

    /** Whether a heads-up is due today: "week" (7 days or fewer), "month" (8 to 30), or null. */
    fun alertStage(days: Int): String? = when (days) {
        in 0..7 -> "week"
        in 8..30 -> "month"
        else -> null
    }

    /** Far-off expiries stay in the Expiries list; the home cards only show ones ending within a month. */
    fun showOnHome(r: KeepReminder, now: Long) = r.kind != KIND || daysLeft(r, now) <= 30

    // ── Finding an end date in saved text, without AI ──────────────────────────────────────

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val keyword = Regex(
        "warranty|guarantee|valid\\s+(?:till|until|upto|up\\s+to|through|thru|to)|expires?(?:\\s+on)?|expiry(?:\\s+date)?|expiration(?:\\s+date)?",
        RegexOption.IGNORE_CASE
    )
    private val dayMonthName = Regex("(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-/.,]+([A-Za-z]{3,9})\\.?[\\s\\-/.,]+(\\d{4})")
    private val monthNameDay = Regex("([A-Za-z]{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})")
    private val numeric = Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})")
    private val iso = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

    private fun monthOf(name: String): Int? =
        months.indexOf(name.lowercase(Locale.ROOT).take(3)).takeIf { it >= 0 && name.length >= 3 }?.plus(1)

    private fun at9(y: Int, m: Int, d: Int, zone: TimeZone): Long? = runCatching {
        LocalDate.of(y, m, d).atTime(9, 0).atZone(zone.toZoneId()).toInstant().toEpochMilli()
    }.getOrNull()

    /** The first date written within a few words after [window] starts, as (position, time). */
    private fun firstDate(window: String, zone: TimeZone): Pair<Int, Long>? {
        val found = mutableListOf<Pair<Int, Long>>()
        dayMonthName.find(window)?.let { m ->
            val mo = monthOf(m.groupValues[2])
            if (mo != null) at9(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt(), zone)?.let { found += m.range.first to it }
        }
        monthNameDay.find(window)?.let { m ->
            val mo = monthOf(m.groupValues[1])
            if (mo != null) at9(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt(), zone)?.let { found += m.range.first to it }
        }
        numeric.find(window)?.let { m ->
            var a = m.groupValues[1].toInt()
            var b = m.groupValues[2].toInt()
            if (b > 12) { val t = a; a = b; b = t }   // 03/25/2027 is month first; otherwise day first
            at9(m.groupValues[3].toInt(), b, a, zone)?.let { found += m.range.first to it }
        }
        iso.find(window)?.let { m ->
            at9(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), zone)?.let { found += m.range.first to it }
        }
        return found.minByOrNull { it.first }
    }

    /**
     * An end date written next to words like "warranty", "valid till" or "expires on" ("Warranty valid till 12 Mar 2027").
     * Only dates today or later count. Day first unless the second number cannot be a month.
     */
    fun find(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        val today = startOfDay(now, zone)
        for (k in keyword.findAll(text)) {
            val window = text.substring(k.range.last + 1, minOf(text.length, k.range.last + 1 + 45)).substringBefore('\n')
            val hit = firstDate(window, zone) ?: continue
            if (hit.second >= today) return hit.second
        }
        return null
    }
}
