package com.keepsakeai.app.people

import com.keepsakeai.app.data.preferences.Entity
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.startOfDay
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone

/** Gentle nudges about people in your notes: birthdays coming up and friends you haven't mentioned in a while. Pure and unit-tested. */
object PeopleNudges {
    const val QUIET_DAYS = 45
    const val MIN_MENTIONS = 3

    data class Birthday(val entity: Entity, val daysAway: Int, val memoryId: Long)
    data class CatchUp(val entity: Entity, val daysQuiet: Int, val memoryId: Long)

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val cue = Regex("birthday|b'day|bday|born on|born:", RegexOption.IGNORE_CASE)
    private val dayMonth = Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?([A-Za-z]{3,9})")
    private val monthDay = Regex("([A-Za-z]{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
    private val numeric = Regex("(\\d{1,2})[/\\-](\\d{1,2})(?:[/\\-]\\d{2,4})?")

    private fun monthOf(name: String): Int? =
        if (name.length < 3) null else months.indexOf(name.lowercase(Locale.ROOT).take(3)).takeIf { it >= 0 }?.plus(1)

    /** (month, day) written after a birthday cue in [text], or null. Day first for numbers unless the second can't be a month. */
    fun birthdayOf(text: String): Pair<Int, Int>? {
        val c = cue.find(text) ?: return null
        val rest = text.substring(c.range.last + 1).take(40)
        dayMonth.find(rest)?.let { m -> monthOf(m.groupValues[2])?.let { mo -> return valid(mo, m.groupValues[1].toInt()) } }
        monthDay.find(rest)?.let { m -> monthOf(m.groupValues[1])?.let { mo -> return valid(mo, m.groupValues[2].toInt()) } }
        numeric.find(rest)?.let { m ->
            var d = m.groupValues[1].toInt(); var mo = m.groupValues[2].toInt()
            if (mo > 12) { val t = d; d = mo; mo = t }
            return valid(mo, d)
        }
        return null
    }

    private fun valid(month: Int, day: Int): Pair<Int, Int>? =
        if (month in 1..12 && day in 1..31 && runCatching { LocalDate.of(2024, month, day) }.isSuccess) month to day else null

    /** People whose birthday falls within [withinDays] days, soonest first. */
    fun birthdays(entities: List<Entity>, now: Long, withinDays: Int = 7, zone: TimeZone = TimeZone.getDefault()): List<Birthday> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone.toZoneId()).toLocalDate()
        return entities.filter { it.type == "person" && !it.hidden }.mapNotNull { e ->
            val note = e.notes.sortedByDescending { it.at }.firstOrNull { birthdayOf(it.note) != null } ?: return@mapNotNull null
            val (mo, d) = birthdayOf(note.note)!!
            fun on(y: Int): LocalDate = runCatching { LocalDate.of(y, mo, d) }.getOrElse { LocalDate.of(y, 3, 1) }   // 29 Feb in a non-leap year
            val next = on(today.year).let { if (it.isBefore(today)) on(today.year + 1) else it }
            val away = java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
            if (away in 0..withinDays) Birthday(e, away, note.memoryId) else null
        }.sortedBy { it.daysAway }
    }

    /** People you mention often but haven't for [QUIET_DAYS] days, most-mentioned first. */
    fun catchUps(entities: List<Entity>, now: Long): List<CatchUp> =
        entities.filter { it.type == "person" && !it.hidden && it.memoryIds.size >= MIN_MENTIONS && it.lastSeen > 0 }
            .map { e -> Triple(e, ((startOfDay(now) - startOfDay(e.lastSeen)) / DAY).toInt(), e.notes.maxByOrNull { it.at }?.memoryId ?: 0L) }
            .filter { it.second >= QUIET_DAYS }
            .sortedByDescending { it.first.memoryIds.size }
            .map { CatchUp(it.first, it.second, it.third) }

    /** One catch-up a day, rotating through the list so the brief doesn't repeat the same name. */
    fun catchUpToday(entities: List<Entity>, now: Long): CatchUp? {
        val list = catchUps(entities, now)
        if (list.isEmpty()) return null
        return list[(now / DAY).toInt().mod(list.size)]
    }
}
