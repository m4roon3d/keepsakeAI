package com.keepsakeai.app.ai

enum class AiProvider(val displayName: String, val tag: String) {
    GEMINI("Gemini Flash", "@Gemini"),
    OPENAI("ChatGPT (GPT-4o)", "@ChatGPT"),
    PERPLEXITY("Perplexity Sonar", "@Perplexity")
}

data class MindPilotResponse(
    val provider: AiProvider,
    val content: String,
    val isError: Boolean = false,
    /** Ids of saved memories the answer cited as [n]. */
    val cited: List<Long> = emptyList(),
)

data class AnalyzedCapture(
    val title: String,
    val summary: String,
    val collection: String,
    val tags: List<String>,
    val detectedExpense: DetectedExpense? = null,
    /** Lasting facts about the user, e.g. ("Vegetarian", "Food"). */
    val facts: List<Pair<String, String>> = emptyList(),
    /** Ongoing topic this belongs to, e.g. "Goa trip". */
    val thread: String? = null,
    val reminder: ReminderSuggestion? = null,
    val entities: List<EntityMention> = emptyList(),
    val todos: List<TodoSuggestion> = emptyList(),
    /** False for promos and noise (only asked for background sources like email). */
    val worthKeeping: Boolean = true,
    /** Set when the capture is mainly something you could buy. */
    val product: ProductInfo? = null,
    /** Things the user means to do that trove can help with: gift ideas, money owed, watch later, place errands… */
    val intents: List<CaptureIntent> = emptyList(),
)

/**
 * One intent found in a capture. [type] is one of gift, owe, watch, read, place_task, try_place, trip, cancel.
 * [subject]: the item, title, task, place name, destination or service. [person]: who (gift, owe).
 * [place]: where (place_task: "a mall" or a named place; try_place: the area). [direction]: owed_to_me | i_owe.
 */
data class CaptureIntent(
    val type: String,
    val subject: String,
    val person: String? = null,
    val place: String? = null,
    val amount: Double? = null,
    val direction: String? = null,
)

/** A purchasable thing seen in a capture: "Sony WH-1000XM5 headphones". */
data class ProductInfo(val name: String, val brand: String?, val model: String?)

/** A person, place, project or organisation mentioned in a capture. */
data class EntityMention(val name: String, val type: String, val note: String)

/** Something the user needs to do, found in a capture. */
data class TodoSuggestion(val text: String, val due: Long?)

/** A future date found in a capture: a bill due date, an event, a deadline. */
data class ReminderSuggestion(
    val title: String,
    val atMillis: Long,
    val kind: String, // due | event | deadline
    val hasTime: Boolean = true,
)

data class DetectedExpense(
    val merchant: String,
    val amount: Double,
    val currency: String,
    val category: String
)
