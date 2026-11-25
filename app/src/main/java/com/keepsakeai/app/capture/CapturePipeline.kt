package com.keepsakeai.app.capture

import android.net.Uri
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.ai.AnalyzedCapture
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PersonalFactEntity
import com.keepsakeai.app.data.preferences.EntityStore
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.ReminderStore
import com.keepsakeai.app.data.preferences.TodoStore
import com.keepsakeai.app.context.DeviceContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Turns anything captured into a memory plus its expense, facts, people, to-dos and reminder.
 * Shared by the app (notes, photos, links, journal) and background work (email).
 */
class CapturePipeline(private val app: MindSpaceApp) {

    data class Saved(val memoryId: Long?, val analyzed: AnalyzedCapture, val todosAdded: Int, val expenseAdded: Boolean, val aiFailed: Boolean = false)

    private val dao get() = app.database.dao()

    /**
     * @param at when it happened (email date), so timelines and spend land on the right day.
     * @param hint extra instructions for the AI, e.g. how to read an email.
     * @param skipIfNotWorthKeeping for background sources: drop promos the AI marks as not worth keeping.
     */
    suspend fun save(
        text: String,
        source: CaptureSource = CaptureSource.TEXT_NOTE,
        url: String? = null,
        photoUri: String? = null,
        at: Long? = null,
        hint: String? = null,
        skipIfNotWorthKeeping: Boolean = false,
        geo: GeoTag? = null,
    ): Saved {
        // Shared links: pull the article / video title and text so the memory is useful later.
        val link = url ?: if (source == CaptureSource.EMAIL) null else LinkContentExtractor.extractFirstUrl(text)
        val fetched = link?.let { runCatching { LinkContentExtractor.fetch(it) }.getOrNull() }
        val enriched = if (fetched != null) "$text\n\n${fetched.platform}: ${fetched.title}\n${fetched.bodyText.take(3000)}" else text

        // Where and when the photo or video was taken, so Gemini can name the café or place.
        val tag = withContext(Dispatchers.IO) { locate(photoUri, source, geo) }
        val metaText = tag?.let { MediaMeta.describe(it) }.orEmpty()
        val withMeta = if (metaText.isNotBlank()) "$enriched\n\n$metaText" else enriched
        val placeHint = if (tag?.hasLocation == true) PLACE_HINT else null
        val fullHint = listOfNotNull(hint, placeHint).joinToString("\n").ifBlank { null }

        // Let Gemini see the photo too (Tamil text, handwriting, posters), not just on-device OCR.
        val jpeg = photoUri?.let { p -> withContext(Dispatchers.IO) { ImagePrep.jpegForAi(app, Uri.parse(p)) } }
        val analyzed = app.mindPilotClient.analyzeCapture(withMeta, jpeg, fullHint, grounded = placeHint != null)
        if (skipIfNotWorthKeeping) {
            // Background sources need the AI's judgement: without it (no key, rate limit) try again later.
            if ("local-capture" in analyzed.tags) return Saved(null, analyzed, 0, false, aiFailed = true)
            if (!analyzed.worthKeeping) return Saved(null, analyzed, 0, false)
        }

        val embedInput = "${analyzed.title} ${analyzed.summary} $text ${analyzed.tags.joinToString(" ")}"
        val vector = app.vectorSearch.embedText(embedInput)
        val now = System.currentTimeMillis()
        val time = at ?: now

        // Where it was kept: the photo's GPS, when it has one.
        val spot = tag?.takeIf { it.hasLocation }?.let { it.lat!! to it.lng!! }
        val memoryId = dao.insertMemory(
            MemoryEntity(
                title = analyzed.title,
                rawContent = withMeta,
                aiSummary = analyzed.summary,
                collectionName = analyzed.collection,
                source = source,
                sourceUrl = link,
                tagsCsv = analyzed.tags.joinToString(","),
                embeddingCsv = app.vectorSearch.encodeVector(vector),
                threadKey = analyzed.thread?.let { matchThread(it) } ?: analyzed.collection,
                photoUrisCsv = photoUri.orEmpty(),
                createdAt = time,
                lat = spot?.first,
                lng = spot?.second
            )
        )

        var expenseAdded = false
        analyzed.detectedExpense?.let { exp ->
            // The same payment often arrives by SMS and by email: skip if one is already within a day.
            val guard = com.keepsakeai.app.finance.FinanceGuard(dao, com.keepsakeai.app.data.preferences.MoneyPrefs(app))
            val dupe = source == CaptureSource.EMAIL && guard.isDuplicate(exp.amount, null, time, wide = true)
            if (!dupe) {
                dao.insertExpense(
                    ExpenseEntity(
                        merchant = exp.merchant,
                        amount = exp.amount,
                        currency = exp.currency,
                        category = if (com.keepsakeai.app.finance.TransferMatcher.isCardBill("${exp.merchant} ${exp.category}")) "Card bill" else exp.category,
                        sourceMethod = if (source == CaptureSource.EMAIL) EMAIL_METHOD else source.name,
                        timestamp = time,
                        type = guard.typeForMerchant(exp.merchant, exp.category)
                    )
                )
                expenseAdded = true
            }
        }
        // Lasting facts about the user, without duplicates.
        if (analyzed.facts.isNotEmpty()) {
            val known = dao.getFacts().map { it.fact.lowercase().trim() }.toSet()
            analyzed.facts.filter { it.first.lowercase().trim() !in known }.forEach { (fact, cat) ->
                dao.insertFact(PersonalFactEntity(fact = fact, category = cat, sourceMemoryId = memoryId))
            }
        }
        // People, places & projects, to-dos and a reminder found in the capture.
        val entities = EntityStore(app)
        analyzed.entities.forEach { e -> entities.addMention(e.type, e.name, e.note, memoryId, time) }
        val todos = TodoStore(app)
        val newTodos = analyzed.todos.mapNotNull { t -> todos.add(t.text, t.due, memoryId) }
        analyzed.reminder?.let { r ->
            ReminderStore(app).add(KeepReminder(id = now + memoryId, title = r.title, atMillis = r.atMillis, kind = r.kind, memoryId = memoryId, hasTime = r.hasTime))
        }
        return Saved(memoryId, analyzed, newTodos.size, expenseAdded)
    }

    /** GPS from the photo itself; for a photo taken just now with no GPS, the phone's current location. */
    private suspend fun locate(photoUri: String?, source: CaptureSource, given: GeoTag?): GeoTag? {
        var tag = given ?: photoUri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path?.let { File(it) }
            ?.takeIf { it.exists() }?.let { MediaMeta.readPhoto(it) }
        val justTaken = tag?.takenAt?.let { System.currentTimeMillis() - it in 0..10 * 60_000L } == true
        if (tag?.hasLocation != true && source == CaptureSource.CAMERA_SCAN && justTaken) {
            DeviceContext.currentLatLng(app)?.let { (lat, lng) -> tag = (tag ?: GeoTag(null, null, null)).copy(lat = lat, lng = lng) }
        }
        val t = tag ?: return null
        if (t.hasLocation && t.place == null) {
            return t.copy(place = MediaMeta.address(app, t.lat!!, t.lng!!)?.ifBlank { null })
        }
        return t
    }

    /** Reuse an existing thread name when the AI words it slightly differently. */
    private suspend fun matchThread(name: String): String {
        val existing = dao.getRecentMemories(300).map { it.threadKey }.distinct()
        val n = name.trim().lowercase()
        return existing.firstOrNull { it.lowercase() == n || it.lowercase().contains(n) || n.contains(it.lowercase()) && it.length > 3 }
            ?: name.trim().replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val EMAIL_METHOD = "Email"
        private val PLACE_HINT = """
            The capture has the GPS location where it was taken (see "Location:"). Use Google Search to work out the specific
            restaurant, café, shop or venue at or right next to those coordinates, especially when it shows food, drinks, a menu,
            a bill or a storefront. Name it in the title and summary (say "likely" if you aren't sure), name the dish or item if
            you can recognise it, and add the venue as an entity of type "place" with a note like "had filter coffee here".
            If it's not at a venue, use the neighbourhood instead.
        """.trimIndent()
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
