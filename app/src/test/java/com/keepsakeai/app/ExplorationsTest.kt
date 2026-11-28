package com.keepsakeai.app

import com.keepsakeai.app.explore.Explorations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationsTest {
    private val card = "Data Engineering Bootcamp\nwww.example-course.in\nWeekend batches"

    @Test fun plainCardHasNoExplorations() {
        val (base, items) = Explorations.split(card)
        assertEquals(card, base)
        assertTrue(items.isEmpty())
    }

    @Test fun addThenSplitRoundTrips() {
        val a = Explorations.Item("28 Nov 2026", "What is the main syllabus?", "1. SQL\n2. Spark\n\n3. Airflow")
        val b = Explorations.Item("29 Nov 2026", "How long is it?", "16 weeks.")
        val raw = Explorations.add(Explorations.add(card, a), b)
        val (base, items) = Explorations.split(raw)
        assertEquals(card, base)
        assertEquals(listOf(a, b), items)
    }

    @Test fun removeKeepsTheRest() {
        val a = Explorations.Item("28 Nov 2026", "Q1", "A1")
        val b = Explorations.Item("28 Nov 2026", "Q2", "A2")
        val raw = Explorations.remove(Explorations.add(Explorations.add(card, a), b), 0)
        assertEquals(listOf(b), Explorations.split(raw).second)
        assertEquals(card, Explorations.split(raw).first)
    }

    @Test fun multiLineQuestionIsKeptOnOneLine() {
        val raw = Explorations.add(card, Explorations.Item("28 Nov 2026", "What is\nthe syllabus?", "SQL"))
        assertEquals("What is the syllabus?", Explorations.split(raw).second.single().question)
    }

    @Test fun cleanDropsCitations() =
        assertEquals("Spark and Airflow are covered.", Explorations.clean("Spark [1] and Airflow [2] are covered."))

    @Test fun whereToBuyReplacesTheOlderAnswerAndKeepsOthers() {
        val q = Explorations.Item("28 Nov 2026", "Is it good for travel?", "Yes.")
        var raw = Explorations.add(card, q)
        raw = Explorations.setWhereToBuy(raw, "28 Nov 2026", "Sony WH-1000XM5", "Price: ₹26,990")
        raw = Explorations.setWhereToBuy(raw, "30 Nov 2026", "Sony WH-1000XM5", "Price: ₹24,990")
        val items = Explorations.split(raw).second
        assertEquals(2, items.size)
        assertEquals(q, items[0])
        assertEquals("Price: ₹24,990", items[1].answer)
        assertEquals("Sony WH-1000XM5", Explorations.whereToBuyProduct(raw))
    }
}
