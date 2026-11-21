package com.keepsakeai.app.ai

import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.preferences.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MultiModelMindPilotClient(private val keyStore: ApiKeyStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Analyzes any captured text, OCR screenshot, or voice transcript.
     * Falls back to deterministic local heuristics if no API key is configured.
     */
    suspend fun analyzeCapture(rawText: String): AnalyzedCapture = withContext(Dispatchers.IO) {
        val geminiKey = keyStore.geminiKey
        if (geminiKey.isNotBlank()) {
            val prompt = """
                Analyze this captured content and return ONLY valid JSON with keys:
                "title" (short 3-6 words),
                "summary" (1-2 crisp sentences),
                "collection" (one of: "Travel", "Study Notes", "Events", "Personal Projects", "Receipts", "Watch Later", "General"),
                "tags" (array of 2-4 short lowercase tags),
                "expense" (null, OR object {"merchant": string, "amount": number, "currency": string, "category": one of "Food", "Transport", "Shopping", "Bills", "Entertainment", "Other"}).
                
                Captured content:
                $rawText
            """.trimIndent()

            val result = callGemini(geminiKey, prompt)
            if (!result.isError) {
                parseAnalyzedJson(result.content, rawText)?.let { return@withContext it }
            }
        }
        localHeuristicAnalyze(rawText)
    }

    /**
     * Mind Pilot Ask Mode: queries selected AI models in parallel using saved MindSpace memories as context.
     */
    suspend fun askMindPilot(
        query: String,
        providers: Set<AiProvider>,
        memories: List<MemoryEntity>,
        agentMode: Boolean
    ): List<MindPilotResponse> = coroutineScope {
        val contextBlock = if (memories.isEmpty()) {
            "No saved memories yet."
        } else {
            memories.take(12).joinToString("\n") {
                "- [${it.collectionName}] ${it.title}: ${it.aiSummary}"
            }
        }

        val fullPrompt = if (agentMode) {
            """
            You are AI Mind Pilot in Agent Mode. Using the user's saved MindSpace context below, produce a concrete, step-by-step execution plan (including file/itinerary/action structure) to accomplish their goal.
            
            Saved Context:
            $contextBlock
            
            Goal: $query
            """.trimIndent()
        } else {
            """
            You are AI Mind Pilot. Answer the user's question concisely using their saved MindSpace context below when relevant.
            
            Saved Context:
            $contextBlock
            
            Question: $query
            """.trimIndent()
        }

        providers.map { provider ->
            async(Dispatchers.IO) {
                when (provider) {
                    AiProvider.GEMINI -> {
                        val key = keyStore.geminiKey
                        if (key.isBlank()) localFallbackResponse(provider, query, memories, agentMode)
                        else callGemini(key, fullPrompt)
                    }
                    AiProvider.OPENAI -> {
                        val key = keyStore.openAiKey
                        if (key.isBlank()) localFallbackResponse(provider, query, memories, agentMode)
                        else callOpenAi(key, fullPrompt)
                    }
                    AiProvider.PERPLEXITY -> {
                        val key = keyStore.perplexityKey
                        if (key.isBlank()) localFallbackResponse(provider, query, memories, agentMode)
                        else callPerplexity(key, fullPrompt)
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Google retires Gemini model IDs regularly, so try a list of candidates and
     * remember whichever one answers. If Google's 404 names a replacement model,
     * that one is tried first.
     */
    @Volatile private var workingGeminiModel: String? = null

    private val geminiModelCandidates = listOf(
        "gemini-flash-latest",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    )

    private fun callGemini(apiKey: String, prompt: String): MindPilotResponse {
        val bodyJson = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        ).toString()

        val queue = ArrayDeque<String>()
        workingGeminiModel?.let { queue.add(it) }
        geminiModelCandidates.forEach { if (it !in queue) queue.add(it) }
        val tried = mutableSetOf<String>()
        var lastError = "Gemini error: no model available"

        while (queue.isNotEmpty()) {
            val model = queue.removeFirst()
            if (!tried.add(model)) continue
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val outcome = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.code == 404 || (resp.code == 400 && raw.contains("model", ignoreCase = true) && raw.contains("not", ignoreCase = true))) {
                        Regex("""use models/([A-Za-z0-9.\-]+)""").find(raw)?.groupValues?.get(1)?.let { suggested ->
                            if (suggested !in tried) queue.addFirst(suggested)
                        }
                        lastError = "Gemini error (${resp.code}) for $model"
                        return@use null
                    }
                    if (!resp.isSuccessful) {
                        return MindPilotResponse(AiProvider.GEMINI, "Gemini error (${resp.code}): $raw", isError = true)
                    }
                    val parts = JSONObject(raw)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                    val text = buildString {
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            if (p.optBoolean("thought", false)) continue
                            append(p.optString("text"))
                        }
                    }
                    workingGeminiModel = model
                    MindPilotResponse(AiProvider.GEMINI, text.trim())
                }
            }.getOrElse {
                return MindPilotResponse(AiProvider.GEMINI, "Network error: ${it.localizedMessage}", isError = true)
            }
            if (outcome != null) return outcome
        }
        return MindPilotResponse(AiProvider.GEMINI, lastError, isError = true)
    }

    private fun callOpenAi(apiKey: String, prompt: String): MindPilotResponse {
        val bodyJson = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        return runCatching {
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return MindPilotResponse(AiProvider.OPENAI, "OpenAI error (${resp.code})", isError = true)
                }
                val text = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                MindPilotResponse(AiProvider.OPENAI, text.trim())
            }
        }.getOrElse {
            MindPilotResponse(AiProvider.OPENAI, "Network error: ${it.localizedMessage}", isError = true)
        }
    }

    private fun callPerplexity(apiKey: String, prompt: String): MindPilotResponse {
        val bodyJson = JSONObject()
            .put("model", "sonar")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        return runCatching {
            val req = Request.Builder()
                .url("https://api.perplexity.ai/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return MindPilotResponse(AiProvider.PERPLEXITY, "Perplexity error (${resp.code})", isError = true)
                }
                val text = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                MindPilotResponse(AiProvider.PERPLEXITY, text.trim())
            }
        }.getOrElse {
            MindPilotResponse(AiProvider.PERPLEXITY, "Network error: ${it.localizedMessage}", isError = true)
        }
    }

    private fun parseAnalyzedJson(rawResponse: String, fallbackText: String): AnalyzedCapture? {
        return runCatching {
            val cleaned = rawResponse.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(cleaned)
            val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
            val tags = List(tagsArr.length()) { idx -> tagsArr.getString(idx) }
            val expObj = obj.optJSONObject("expense")
            val expense = expObj?.let {
                DetectedExpense(
                    merchant = it.optString("merchant", "Unknown"),
                    amount = it.optDouble("amount", 0.0),
                    currency = it.optString("currency", keyStore.defaultCurrency),
                    category = it.optString("category", "Other")
                )
            }
            AnalyzedCapture(
                title = obj.optString("title", fallbackText.take(36)),
                summary = obj.optString("summary", fallbackText.take(120)),
                collection = obj.optString("collection", "General"),
                tags = tags,
                detectedExpense = expense?.takeIf { it.amount > 0.0 }
            )
        }.getOrNull()
    }

    private fun localHeuristicAnalyze(rawText: String): AnalyzedCapture {
        val lower = rawText.lowercase()
        val collection = when {
            listOf("flight", "hotel", "trip", "train", "bali", "itinerary").any { it in lower } -> "Travel"
            listOf("paid", "rs", "inr", "usd", "₹", "$", "receipt", "total", "upi").any { it in lower } -> "Receipts"
            listOf("youtube.com", "youtu.be", "tiktok.com").any { it in lower } -> "Watch Later"
            listOf("exam", "notes", "lecture", "study", "chapter").any { it in lower } -> "Study Notes"
            listOf("meeting", "calendar", "event", "ticket").any { it in lower } -> "Events"
            else -> "Personal Projects"
        }

        val amountRegex = Regex("""(?:₹|rs\.?|inr|\$|usd)\s*([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(rawText)
        val detectedExpense = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { amt ->
            val category = when {
                listOf("swiggy", "zomato", "cafe", "coffee", "food", "restaurant").any { it in lower } -> "Food"
                listOf("uber", "ola", "metro", "flight", "fuel", "cab").any { it in lower } -> "Transport"
                listOf("amazon", "flipkart", "store", "mall").any { it in lower } -> "Shopping"
                else -> "Other"
            }
            DetectedExpense(
                merchant = rawText.lineSequence().firstOrNull()?.take(24) ?: "Captured Receipt",
                amount = amt,
                currency = keyStore.defaultCurrency,
                category = category
            )
        }

        val firstLine = rawText.lineSequence().firstOrNull { it.isNotBlank() } ?: "Captured Memory"
        return AnalyzedCapture(
            title = firstLine.take(42),
            summary = rawText.trim().take(140),
            collection = collection,
            tags = listOf(collection.lowercase().replace(" ", "-"), "local-capture"),
            detectedExpense = detectedExpense
        )
    }

    private fun localFallbackResponse(
        provider: AiProvider,
        query: String,
        memories: List<MemoryEntity>,
        agentMode: Boolean
    ): MindPilotResponse {
        val matches = memories.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.rawContent.contains(query, ignoreCase = true) ||
                it.collectionName.contains(query, ignoreCase = true)
        }
        val modeNote = if (agentMode) "[Agent Mode Demo]" else "[Ask Mode Demo]"
        val contextSummary = if (matches.isNotEmpty()) {
            "Matched ${matches.size} saved memories: " + matches.take(3).joinToString("; ") { it.title }
        } else {
            "Found ${memories.size} total saved memories in local Room DB."
        }
        return MindPilotResponse(
            provider = provider,
            content = "$modeNote Add your ${provider.displayName} API key in Settings for live cloud reasoning.\n\nLocal context check: $contextSummary"
        )
    }
}
