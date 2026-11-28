package com.keepsakeai.app

import com.keepsakeai.app.intents.IntentActions
import com.keepsakeai.app.intents.Owed
import com.keepsakeai.app.intents.OwedRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test fun genericVersusNamedPlaces() {
        assertTrue(IntentActions.isGenericPlace("a mall"))
        assertTrue(IntentActions.isGenericPlace("any pharmacy"))
        assertTrue(IntentActions.isGenericPlace("the nearest ATM"))
        assertFalse(IntentActions.isGenericPlace("Phoenix Marketcity"))
        assertFalse(IntentActions.isGenericPlace("Apple store Bangalore"))
    }

    @Test fun locationLineReplacesWhereItWasSaved() {
        val raw = "Must try Brahmin's Coffee Bar\n\nLocation: 12.90000, 77.58000 (near J. P. Nagar)"
        val out = IntentActions.withLocation(raw, 12.95412, 77.56901, "Brahmin's Coffee Bar")
        assertEquals("Must try Brahmin's Coffee Bar\n\nLocation: 12.95412, 77.56901 (near Brahmin's Coffee Bar)", out)
        assertTrue(IntentActions.withLocation("no place yet", 1.0, 2.0, "X").endsWith("Location: 1.00000, 2.00000 (near X)"))
    }

    @Test fun owedNudgesAfterTwoWeeksAndNets() {
        val a = Owed(1, "Ravi", 2000.0, true, "", 1, now - 15 * day)
        val b = Owed(2, "ravi", 500.0, false, "dinner", 2, now - 3 * day)
        val c = Owed(3, "Meera", 300.0, true, "", 3, now - 20 * day, settled = true)
        assertEquals(listOf(1L), OwedRules.dueForNudge(listOf(a, b, c), now).map { it.id })
        assertEquals(mapOf("ravi" to 1500.0), OwedRules.netByPerson(listOf(a, b, c)))
        assertEquals(listOf(1L, 2L), OwedRules.open(listOf(b, a, c)).map { it.id })
    }
}
