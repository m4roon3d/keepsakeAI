package com.keepsakeai.app

import com.keepsakeai.app.data.preferences.Entity
import com.keepsakeai.app.data.preferences.Todo
import com.keepsakeai.app.data.preferences.TodoBucket
import com.keepsakeai.app.data.preferences.bucketOf
import com.keepsakeai.app.data.preferences.matchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrainStoresTest {
    private val people = listOf(Entity("Ravi Kumar", "person"), Entity("Indiranagar", "place"), Entity("Goa trip", "project"))

    @Test
    fun matchesShortAndFullNamesOfTheSameType() {
        assertEquals("Ravi Kumar", matchEntity(people, "person", "Ravi")!!.name)
        assertEquals("Ravi Kumar", matchEntity(people, "person", "ravi kumar")!!.name)
        assertNull(matchEntity(people, "place", "Ravi"))
        assertNull(matchEntity(people, "person", "Ravindra"))
        assertNull(matchEntity(people, "person", "Ra"))
    }

    @Test
    fun todosLandInTheRightBucket() {
        val day = 24L * 60 * 60 * 1000
        val today = 1_795_000_000_000L - (1_795_000_000_000L % day)
        val now = today + 10 * 60 * 60 * 1000
        fun t(due: Long?, done: Boolean = false) = Todo(1, "x", due, done)
        assertEquals(TodoBucket.OVERDUE, bucketOf(t(today - 1), now, today))
        assertEquals(TodoBucket.TODAY, bucketOf(t(today + 9 * 60 * 60 * 1000), now, today))
        assertEquals(TodoBucket.SOON, bucketOf(t(today + 3 * day), now, today))
        assertEquals(TodoBucket.LATER, bucketOf(t(today + 20 * day), now, today))
        assertEquals(TodoBucket.SOMEDAY, bucketOf(t(null), now, today))
        assertEquals(TodoBucket.DONE, bucketOf(t(today - 1, done = true), now, today))
    }
}
