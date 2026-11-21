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
    suspend fun analyzeCapture(rawText: String, imageJpeg: ByteArray? = null): AnalyzedCapture = withContext(Dispatchers.IO) {
        val geminiKey = keyStore.geminiKey
        if (geminiKey.isNotBlank()) {
            val now = java.text.SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", java.util.Locale.ENGLISH).format(java.util.Date())
            val prompt = """
                Today is $now (timezone ${java.util.TimeZone.getDefault().id}).
                {{PHOTO}}
                Analyze this captured content and return ONLY valid JSON with keys:
                "title" (short 3-6 words),
                "summary" (1-2 crisp sentences),
                "collection" (one of: "Travel", "Study Notes", "Events", "Personal Projects", "Receipts", "Watch Later", "General"),
                "tags" (array of 2-4 short lowercase tags),
                "expense" (null, OR object {"merchant": string, "amount": number, "currency": string, "category": one of "Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Other"}),
                "thread" (2-4 word name of the ongoing trip, project, event or topic this belongs to, e.g. "Goa trip", "Home loan", "Spark course"; null if standalone),
                "facts" (array, at most 3, of lasting facts ABOUT THE USER that the content clearly states, as objects {"fact": short sentence, "category": one of "Profile", "Preferences", "Health", "Work", "People", "Places", "Money"}; empty array if none — never guess),
                "reminder" (null, OR {"title": short action like "Pay BESCOM bill" or "Harris Jayaraj concert", "datetime": ISO 8601 local date-time like 2026-11-28T19:00 (use 09:00 when no time is given), "has_time": true only if a start time is actually stated, "kind": "due" | "event" | "deadline"} when the content has a specific FUTURE date worth remembering; for posters and tickets put the venue in the summary).

                Captured content:
                {{RAW}}
            """.trimIndent().replace("{{RAW}}", rawText.take(6000)).replace(
                "{{PHOTO}}",
                if (imageJpeg != null) "A photo of the capture is attached. Read ALL text in it in any language (Tamil, Hindi, Kannada, handwriting, stylised poster fonts) plus visual details, and combine it with the on-device text below, which may be incomplete or garbled. Write the title and summary in English." else ""
            )

            val result = callGemini(geminiKey, prompt, feature = "sort capture", imageJpeg = imageJpeg)
            if (!result.isError) {
                parseAnalyzedJson(result.content, rawText)?.let { return@withContext it }
            }
        }
        localHeuristicAnalyze(rawText)
    }

    /**
     * Sorts payee names the keyword rules couldn't place, in one Gemini call.
     * Returns name → category; empty when there's no key or the call fails.
     */
    suspend fun categorizeMerchants(names: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val key = keyStore.geminiKey
        if (key.isBlank() || names.isEmpty()) return@withContext emptyMap()
        val allowed = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Other")
        val prompt = buildString {
            append("Classify each Indian merchant or UPI payee below into exactly one of: ")
            append(allowed.joinToString(", "))
            append(". Person names and phone-number payees are Other. ")
            append("Reply with ONLY a JSON object mapping each name exactly as given to its category.\n\n")
            names.forEach { append(it).append('\n') }
        }
        val r = callGemini(key, prompt, feature = "sort payees")
        if (r.isError) return@withContext emptyMap()
        runCatching {
            val json = r.content.substringAfter('{', "").substringBeforeLast('}', "")
            val obj = JSONObject("{$json}")
            names.mapNotNull { n -> obj.optString(n).takeIf { it in allowed }?.let { n to it } }.toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * Mind Pilot Ask Mode: queries selected AI models in parallel using saved MindSpace memories as context.
     */
    suspend fun askMindPilot(
        query: String,
        providers: Set<AiProvider>,
        memories: List<MemoryEntity>,
        agentMode: Boolean,
        deviceContext: String = "",
        moneyContext: String = "",
        factsContext: String = ""
    ): List<MindPilotResponse> = coroutineScope {
        val contextBlock = if (memories.isEmpty()) {
            "No saved memories yet."
        } else {
            val df = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
            memories.take(12).mapIndexed { i, m ->
                "[${i + 1}] ${m.title} (${m.collectionName}, saved ${df.format(java.util.Date(m.createdAt))}): ${m.aiSummary}" +
                    m.rawContent.take(400).replace("\n", " ").let { if (it.isNotBlank() && it != m.aiSummary) " | details: $it" else "" }
            }.joinToString("\n")
        }

        val fullPrompt = if (agentMode) {
            """
            You are AI Mind Pilot in Agent Mode. Using the user's saved MindSpace context below, produce a concrete, step-by-step execution plan (including file/itinerary/action structure) to accomplish their goal.
            
            {{DEVICE}}

            What you know about the user (facts they taught keepsake):
            {{FACTS}}

            The user's spending (computed by the app; trust these totals, quote amounts in ₹,
            and say when the data doesn't cover what was asked):
            {{MONEY}}

            Saved Context:
            {{CONTEXT}}
            
            Goal: {{QUERY}}
            """.trimIndent().replace("{{DEVICE}}", deviceContext).replace("{{CONTEXT}}", contextBlock).replace("{{QUERY}}", query).replace("{{MONEY}}", moneyContext.ifBlank { "No payments recorded." }).replace("{{FACTS}}", factsContext.ifBlank { "None yet." })
        } else {
            """
            You are keepsake Pilot, the user's personal assistant. Answer concisely.
            Use the device context (date, time, location) for anything time- or place-dependent,
            search the web for live information such as weather, news or opening hours,
            and use the user's saved memories when relevant. Never ask for the user's location
            if it is given below. When you use a saved memory, cite it inline by its number, like [2].
            Only cite numbers listed under Saved Context, and never invent memories.

            {{DEVICE}}

            What you know about the user (facts they taught keepsake):
            {{FACTS}}

            The user's spending (computed by the app; trust these totals, quote amounts in ₹,
            and say when the data doesn't cover what was asked):
            {{MONEY}}

            Saved Context:
            {{CONTEXT}}
            
            Question: {{QUERY}}
            """.trimIndent().replace("{{DEVICE}}", deviceContext).replace("{{CONTEXT}}", contextBlock).replace("{{QUERY}}", query).replace("{{MONEY}}", moneyContext.ifBlank { "No payments recorded." }).replace("{{FACTS}}", factsContext.ifBlank { "None yet." })
        }

        providers.map { provider ->
            async(Dispatchers.IO) {
                when (provider) {
                    AiProvider.GEMINI -> {
                        val key = keyStore.geminiKey
                        if (key.isBlank()) localFallbackResponse(provider, query, memories, agentMode)
                        else callGemini(key, fullPrompt, grounded = true)
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

    /** Every outgoing AI call is reported here (provider, feature, prompt) for the activity log. */
    var auditSink: ((provider: String, feature: String, prompt: String) -> Unit)? = null

    private fun audit(provider: String, feature: String, prompt: String) {
        runCatching { auditSink?.invoke(provider, feature, prompt) }
    }

    private fun callGemini(
        apiKey: String,
        prompt: String,
        grounded: Boolean = false,
        feature: String = "ask pilot",
        imageJpeg: ByteArray? = null,
    ): MindPilotResponse {
        audit("Gemini", feature + (if (grounded) " + web search" else "") + (if (imageJpeg != null) " + photo" else ""), prompt)
        val parts = JSONArray()
        if (imageJpeg != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", android.util.Base64.encodeToString(imageJpeg, android.util.Base64.NO_WRAP))
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))
        val bodyObj = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        if (grounded) {
            // Live Google Search grounding: weather, news, places.
            bodyObj.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        val bodyJson = bodyObj.toString()

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
                        if (grounded && resp.code == 400) return callGemini(apiKey, prompt, grounded = false, feature = feature, imageJpeg = imageJpeg)
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
        audit("ChatGPT", "ask pilot", prompt)
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
        audit("Perplexity", "ask pilot", prompt)
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
            val start = rawResponse.indexOf('{')
            val end = rawResponse.lastIndexOf('}')
            val obj = JSONObject(rawResponse.substring(start, end + 1))
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
                detectedExpense = expense?.takeIf { it.amount > 0.0 },
                facts = obj.optJSONArray("facts")?.let { arr ->
                    List(arr.length()) { i -> arr.optJSONObject(i) }
                        .mapNotNull { o -> o?.optString("fact")?.takeIf { it.isNotBlank() }?.let { it to o.optString("category", "Profile") } }
                        .take(3)
                } ?: emptyList(),
                thread = obj.optString("thread").takeIf { it.isNotBlank() && it != "null" },
                reminder = obj.optJSONObject("reminder")?.let { r ->
                    val at = runCatching {
                        java.time.LocalDateTime.parse(r.optString("datetime").take(16))
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull()
                    val title = r.optString("title")
                    if (at != null && at > System.currentTimeMillis() - 12 * 60 * 60 * 1000 && title.isNotBlank())
                        ReminderSuggestion(title.take(60), at, r.optString("kind", "event"), r.optBoolean("has_time", true)) else null
                }
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
