package com.keepsakeai.app.ai

enum class AiProvider(val displayName: String, val tag: String) {
    GEMINI("Gemini Flash", "@Gemini"),
    OPENAI("ChatGPT (GPT-4o)", "@ChatGPT"),
    PERPLEXITY("Perplexity Sonar", "@Perplexity")
}

data class MindPilotResponse(
    val provider: AiProvider,
    val content: String,
    val isError: Boolean = false
)

data class AnalyzedCapture(
    val title: String,
    val summary: String,
    val collection: String,
    val tags: List<String>,
    val detectedExpense: DetectedExpense? = null
)

data class DetectedExpense(
    val merchant: String,
    val amount: Double,
    val currency: String,
    val category: String
)
