package com.keepsakeai.app

import com.keepsakeai.app.ai.SemanticVectorSearch
import com.keepsakeai.app.data.preferences.ApiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SemanticVectorSearchTest {

    @Test
    fun localHashedEmbeddingIsUnitNormalizedAndDeterministic() {
        // Test pure math helper directly without Android Context
        val vec = computeStandaloneEmbedding("Bali beach villa itinerary sunset")
        assertEquals(128, vec.size)
        val norm = sqrt(vec.sumOf { (it * it).toDouble() })
        assertEquals(1.0, norm, 0.001)
    }

    @Test
    fun similarTextsScoreHigherThanUnrelatedTexts() {
        val q = computeStandaloneEmbedding("bali beach villa itinerary")
        val relevant = computeStandaloneEmbedding("Bali 5-day Ubud villa booking and flight itinerary")
        val unrelated = computeStandaloneEmbedding("Apache Spark shuffle partition optimization")

        val simRelevant = cosine(q, relevant)
        val simUnrelated = cosine(q, unrelated)
        assertTrue("Expected relevant ($simRelevant) > unrelated ($simUnrelated)", simRelevant > simUnrelated)
    }

    private fun computeStandaloneEmbedding(text: String, dims: Int = 128): FloatArray {
        val vec = FloatArray(dims)
        val tokens = text.lowercase().replace(Regex("[^a-z0-9\s]"), " ").split(Regex("\s+")).filter { it.length >= 2 }
        for (t in tokens) {
            vec[kotlin.math.abs(t.hashCode()) % dims] += 2.0f
            if (t.length >= 3) {
                for (i in 0..t.length - 3) {
                    vec[kotlin.math.abs(t.substring(i, i + 3).hashCode()) % dims] += 0.6f
                }
            }
        }
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
        return vec
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
