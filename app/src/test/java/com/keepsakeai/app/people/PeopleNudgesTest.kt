package com.keepsakeai.app.people

import com.keepsakeai.app.data.preferences.Entity
import com.keepsakeai.app.data.preferences.EntityNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class PeopleNudgesTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(10, 0).atZone(zone.toZoneId()).toInstant().toEpochMilli()
    private val now = ms(2026, 11, 25)
    private fun person(name: String, vararg notes: Pair<String, Long>, mems: Int = notes.size, type: String = "person") = Entity(
        name, type, notes.mapIndexed { i, n -> EntityNote(100L + i, n.first, n.second) }, List(mems) { it.toLong() }
    )

    @Test fun parsesBirthdayForms() {
        assertEquals(3 to 28, PeopleNudges.birthdayOf("Priya's birthday is 28 Mar"))
        assertEquals(12 to 1, PeopleNudges.birthdayOf("bday: December 1st"))
        assertEquals(3 to 5, PeopleNudges.birthdayOf("birthday 05/03"))
        assertEquals(3 to 25, PeopleNudges.birthdayOf("birthday 03/25"))
        assertNull(PeopleNudges.birthdayOf("met for coffee on 28 Mar"))
        assertNull(PeopleNudges.birthdayOf("birthday 31 Feb"))
    }

    @Test fun birthdaysWithinAWeekSoonestFirst() {
        val list = PeopleNudges.birthdays(listOf(
            person("Asha", "birthday 27 Nov" to now), person("Ravi", "birthday 25 Nov" to now),
            person("Far", "birthday 10 Dec" to now), person("Acme", "birthday 26 Nov" to now, type = "org")
        ), now, zone = zone)
        assertEquals(listOf("Ravi" to 0, "Asha" to 2), list.map { it.entity.name to it.daysAway })
    }

    @Test fun birthdayWrapsIntoNextYear() {
        val late = ms(2026, 12, 29)
        assertEquals(5, PeopleNudges.birthdays(listOf(person("Mira", "birthday 3 Jan" to late)), late, zone = zone).single().daysAway)
    }

    @Test fun catchUpNeedsOftenMentionedAndQuiet() {
        val old = now - 60L * 24 * 60 * 60 * 1000
        val recent = now - 5L * 24 * 60 * 60 * 1000
        val list = PeopleNudges.catchUps(listOf(
            person("Quiet", "lunch" to old, mems = 5), person("Rare", "lunch" to old, mems = 1),
            person("Recent", "lunch" to recent, mems = 6), person("Place", "x" to old, mems = 9, type = "place")
        ), now)
        assertEquals(listOf("Quiet"), list.map { it.entity.name })
        assertEquals(60, list.single().daysQuiet)
    }

    @Test fun catchUpTodayRotates() {
        val old = now - 60L * 24 * 60 * 60 * 1000
        val es = listOf(person("A", "x" to old, mems = 5), person("B", "x" to old, mems = 4))
        val a = PeopleNudges.catchUpToday(es, now)!!.entity.name
        val b = PeopleNudges.catchUpToday(es, now + 24L * 60 * 60 * 1000)!!.entity.name
        assertEquals(setOf("A", "B"), setOf(a, b))
        assertNull(PeopleNudges.catchUpToday(emptyList(), now))
    }
}
