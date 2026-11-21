package com.keepsakeai.app.ai

import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.preferences.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

data class ScoredMemory(
    val memory: MemoryEntity,
    val similarity: Float
)

/**
 * Hybrid On-Device Vector Search:
 * - Generates compact 128-dim local feature-hashed embeddings offline with zero network calls.
 * - Optionally upgrades to Gemini text-embedding-004 when a Gemini API key is configured.
 * - Performs exact cosine similarity ranking in Kotlin over Room-stored vectors.
 */
class SemanticVectorSearch(private val keyStore: ApiKeyStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        val geminiKey = keyStore.geminiKey
        if (geminiKey.isNotBlank()) {
            fetchGeminiEmbedding(geminiKey, text)?.let { return@withContext it }
        }
        computeLocalHashedEmbedding(text)
    }

    suspend fun rankMemories(
        query: String,
        memories: List<MemoryEntity>,
        topK: Int = 8
    ): List<ScoredMemory> = withContext(Dispatchers.Default) {
        if (query.isBlank() || memories.isEmpty()) return@withContext emptyList()
        val queryVec = embedText(query)

        memories.map { mem ->
            val memVec = decodeVector(mem.embeddingCsv).takeIf { it.size == queryVec.size }
                ?: computeLocalHashedEmbedding("${mem.title} ${mem.aiSummary} ${mem.rawContent} ${mem.tagsCsv}")
            val score = if (memVec.size == queryVec.size) cosineSimilarity(queryVec, memVec) else 0f
            ScoredMemory(mem, score)
        }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    fun encodeVector(vec: FloatArray): String =
        vec.joinToString(",") { String.format(Locale.US, "%.4f", it) }

    fun decodeVector(csv: String): FloatArray {
        if (csv.isBlank()) return FloatArray(0)
        return csv.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }

    /**
     * Deterministic 128-dim L2-normalized local embedding using word unigrams + character trigrams.
     * Works 100% offline on-device for instant semantic-ish retrieval.
     */
    fun computeLocalHashedEmbedding(text: String, dims: Int = 128): FloatArray {
        val vec = FloatArray(dims)
        val normalized = text.lowercase(Locale.US).replace(Regex("[^a-z0-9\s]"), " ")
        val tokens = normalized.split(Regex("\s+")).filter { it.length >= 2 }

        for (token in tokens) {
            val h1 = abs(token.hashCode()) % dims
            vec[h1] += 2.0f
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val hTri = abs(tri.hashCode()) % dims
                    vec[hTri] += 0.6f
                }
            }
        }

        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var magA = 0f
        var magB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun fetchGeminiEmbedding(apiKey: String, text: String): FloatArray? {
        return runCatching {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=$apiKey"
            val body = JSONObject()
                .put("model", "models/text-embedding-004")
                .put(
                    "content",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text.take(2000))))
                )
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                val values = JSONObject(raw).getJSONObject("embedding").getJSONArray("values")
                FloatArray(values.length()) { idx -> values.getDouble(idx).toFloat() }
            }
        }.getOrNull()
    }
}
