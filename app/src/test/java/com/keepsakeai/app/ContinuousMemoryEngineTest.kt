package com.keepsakeai.app

import com.keepsakeai.app.ai.ContinuousMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousMemoryEngineTest {

    @Test
    fun distillsBaliTravelThreadAndFacts() {
        val result = ContinuousMemoryEngine.distill(
            rawText = "Booked Indigo BLR to DPS flight for our Ubud Bali trip",
            collectionName = "Travel",
            sourceMemoryId = 42L
        )
        assertEquals("Bali Trip", result.threadKey)
        assertTrue(result.extractedFacts.any { it.category == "Travel" })
        assertTrue(result.extractedFacts.any { it.category == "Profile" })
    }

    @Test
    fun distillsCoffeePreferenceFact() {
        val result = ContinuousMemoryEngine.distill(
            rawText = "Paid Rs 480 at Blue Tokai Indiranagar for pour over coffee",
            collectionName = "Receipts",
            sourceMemoryId = 43L
        )
        assertEquals("Food & Dining", result.threadKey)
        assertTrue(result.extractedFacts.any { it.category == "Preferences" })
    }
}
