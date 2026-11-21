package com.keepsakeai.app.ai

import com.keepsakeai.app.data.local.PersonalFactEntity
import java.util.Locale

data class DistilledMemoryResult(
    val threadKey: String,
    val extractedFacts: List<PersonalFactEntity>
)

/**
 * Distills raw captures into enduring personal facts ("What keepsake remembers")
 * and assigns a Memory Thread key so related captures link into one evolving timeline.
 */
object ContinuousMemoryEngine {

    fun distill(rawText: String, collectionName: String, sourceMemoryId: Long): DistilledMemoryResult {
        val lower = rawText.lowercase(Locale.US)
        val facts = mutableListOf<PersonalFactEntity>()

        val threadKey = when {
            listOf("bali", "ubud", "dps").any { it in lower } -> "Bali Trip"
            listOf("play store", "aab", "keystore", "launch").any { it in lower } -> "App Launch"
            listOf("spark", "pipeline", "architecture", "cdc").any { it in lower } -> "Data Engineering"
            listOf("coffee", "swiggy", "zomato", "biryani", "blinkit").any { it in lower } -> "Food & Dining"
            listOf("bescom", "airtel", "jio", "electricity", "bill").any { it in lower } -> "Recurring Bills"
            else -> collectionName
        }

        if ("bali" in lower || "ubud" in lower) {
            facts.add(
                PersonalFactEntity(
                    fact = "Planning a trip to Bali (Ubud & coastal itinerary)",
                    category = "Travel",
                    sourceMemoryId = sourceMemoryId
                )
            )
        }
        if ("blr" in lower || "bengaluru" in lower || "indiranagar" in lower) {
            facts.add(
                PersonalFactEntity(
                    fact = "Based in Bengaluru (BLR)",
                    category = "Profile",
                    sourceMemoryId = sourceMemoryId
                )
            )
        }
        if (listOf("third wave", "blue tokai", "coffee").any { it in lower }) {
            facts.add(
                PersonalFactEntity(
                    fact = "Frequently visits specialty coffee spots (Third Wave / Blue Tokai)",
                    category = "Preferences",
                    sourceMemoryId = sourceMemoryId
                )
            )
        }
        if (listOf("play store", "aab", "android").any { it in lower }) {
            facts.add(
                PersonalFactEntity(
                    fact = "Building and shipping Android apps on Google Play Store",
                    category = "Projects",
                    sourceMemoryId = sourceMemoryId
                )
            )
        }

        return DistilledMemoryResult(threadKey = threadKey, extractedFacts = facts)
    }
}
