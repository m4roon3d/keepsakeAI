package com.keepsakeai.app.ui

import android.app.Application
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.ai.AiProvider
import com.keepsakeai.app.ai.MindPilotResponse
import com.keepsakeai.app.ai.ScoredMemory
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.DigestSuggestionEntity
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.capture.UpiSmsInboxScanner
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import com.keepsakeai.app.data.local.PersonalFactEntity
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.widget.MindSpaceWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.keepsakeai.app.ui.spend.spendDigest
import com.keepsakeai.app.ui.spend.isMoneyQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MindSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindSpaceApp
    private val dao = app.database.dao()

    val selectedCollection = MutableStateFlow<String?>(null)
    val searchQuery = MutableStateFlow("")
    val semanticResults = MutableStateFlow<List<ScoredMemory>>(emptyList())
    val isCapturing = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)

    val allMemories = dao.observeAllMemories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val collections = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredMemories: StateFlow<List<MemoryEntity>> =
        combine(allMemories, selectedCollection) { list, col ->
            if (col == null) list else list.filter { it.collectionName == col }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = dao.observeAllExpenses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pendingUpi: StateFlow<List<PendingUpiTransactionEntity>> = dao.observePendingUpi()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val suggestions: StateFlow<List<DigestSuggestionEntity>> = dao.observeLatestSuggestions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Mind Pilot state
    val selectedProviders = MutableStateFlow(setOf(AiProvider.GEMINI, AiProvider.OPENAI))
    val agentModeEnabled = MutableStateFlow(false)
    val useMemoryContext = MutableStateFlow(true)
    val isAskingPilot = MutableStateFlow(false)
    val pilotResponses = MutableStateFlow<List<MindPilotResponse>>(emptyList())
    /** Memories handed to Pilot for the current answer, by id (for citation chips). */
    val pilotSources = MutableStateFlow<Map<Long, MemoryEntity>>(emptyMap())
    val currentPlace = MutableStateFlow<String?>(null)
    /** Memory pages open on top of everything; back steps through related memories. */
    val memoryStack = MutableStateFlow<List<Long>>(emptyList())
    fun openMemory(id: Long) { memoryStack.value = memoryStack.value + id }
    fun closeMemory() { memoryStack.value = memoryStack.value.dropLast(1) }
    fun closeAllMemories() { memoryStack.value = emptyList() }

    fun observeMemory(id: Long) = dao.observeMemory(id)

    /** Memories closest in meaning, with same-thread memories first. */
    suspend fun relatedTo(m: MemoryEntity): List<MemoryEntity> {
        val all = dao.getRecentMemories(400).filter { it.id != m.id }
        val sameThread = all.filter { m.threadKey.isNotBlank() && m.threadKey != "General" && it.threadKey == m.threadKey }.take(4)
        val similar = app.vectorSearch.rankMemories("${m.title} ${m.aiSummary} ${m.tagsCsv.replace(',', ' ')}", all, topK = 8)
            .filter { it.similarity > 0.2f }.map { it.memory }
        return (sameThread + similar).distinctBy { it.id }.take(6)
    }

    fun updateMemory(m: MemoryEntity) {
        viewModelScope.launch {
            val vec = app.vectorSearch.embedText("${m.title} ${m.aiSummary} ${m.rawContent.take(500)} ${m.tagsCsv}")
            dao.updateMemory(m.copy(embeddingCsv = app.vectorSearch.encodeVector(vec)))
            statusMessage.value = "Saved changes"
            refreshWidget()
        }
    }

    /** Full-screen overlay currently shown ("review", "ai_log") or null. */
    val overlay = MutableStateFlow<String?>(null)

    val aiLogs = dao.observeAiAuditLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun clearAiLogs() { viewModelScope.launch { dao.clearAiAuditLogs() } }

    /** A question to pre-fill in Pilot (set from Spend, widget or reminders). */
    val pilotDraft = MutableStateFlow<String?>(null)

    fun refreshPlace() {
        viewModelScope.launch { currentPlace.value = DeviceContext.currentPlace(app) }
    }

    fun runSemanticSearch(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            semanticResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val all = dao.getRecentMemories(200)
            semanticResults.value = app.vectorSearch.rankMemories(query, all, topK = 10)
        }
    }

    fun captureText(text: String, source: CaptureSource = CaptureSource.TEXT_NOTE, url: String? = null, photoUri: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            isCapturing.value = true
            // Shared links: pull the article / video title and text so the memory is useful later.
            val link = url ?: com.keepsakeai.app.capture.LinkContentExtractor.extractFirstUrl(text)
            val fetched = link?.let { runCatching { com.keepsakeai.app.capture.LinkContentExtractor.fetch(it) }.getOrNull() }
            val enriched = if (fetched != null) "$text\n\n${fetched.platform}: ${fetched.title}\n${fetched.bodyText.take(3000)}" else text

            // Let Gemini see the photo too (Tamil text, handwriting, posters), not just on-device OCR.
            val jpeg = photoUri?.let { p -> withContext(Dispatchers.IO) { com.keepsakeai.app.capture.ImagePrep.jpegForAi(app, Uri.parse(p)) } }
            val analyzed = app.mindPilotClient.analyzeCapture(enriched, jpeg)
            val embedInput = "${analyzed.title} ${analyzed.summary} $text ${analyzed.tags.joinToString(" ")}"
            val vector = app.vectorSearch.embedText(embedInput)

            val memoryId = dao.insertMemory(
                MemoryEntity(
                    title = analyzed.title,
                    rawContent = enriched,
                    aiSummary = analyzed.summary,
                    collectionName = analyzed.collection,
                    source = source,
                    sourceUrl = link,
                    tagsCsv = analyzed.tags.joinToString(","),
                    embeddingCsv = app.vectorSearch.encodeVector(vector),
                    threadKey = analyzed.thread?.let { matchThread(it) } ?: analyzed.collection,
                    photoUrisCsv = photoUri.orEmpty()
                )
            )
            analyzed.detectedExpense?.let { exp ->
                dao.insertExpense(
                    ExpenseEntity(
                        merchant = exp.merchant,
                        amount = exp.amount,
                        currency = exp.currency,
                        category = exp.category,
                        sourceMethod = source.name
                    )
                )
            }
            // Lasting facts about the user, without duplicates.
            if (analyzed.facts.isNotEmpty()) {
                val known = dao.getFacts().map { it.fact.lowercase().trim() }.toSet()
                analyzed.facts.filter { it.first.lowercase().trim() !in known }.forEach { (fact, cat) ->
                    dao.insertFact(PersonalFactEntity(fact = fact, category = cat, sourceMemoryId = memoryId))
                }
            }
            // People, places & projects, and to-dos found in the capture
            analyzed.entities.forEach { e -> entityStore.addMention(e.type, e.name, e.note, memoryId, System.currentTimeMillis()) }
            val newTodos = analyzed.todos.mapNotNull { t -> todoStore.add(t.text, t.due, memoryId) }
            if (analyzed.entities.isNotEmpty()) entities.value = entityStore.all()
            if (newTodos.isNotEmpty()) todos.value = todoStore.all()
            analyzed.reminder?.let { r ->
                reminderStore.add(KeepReminder(id = System.currentTimeMillis(), title = r.title, atMillis = r.atMillis, kind = r.kind, memoryId = memoryId, hasTime = r.hasTime))
                reminders.value = reminderStore.all()
            }
            refreshWidget()
            statusMessage.value = when {
                analyzed.reminder != null -> "Saved · found a date: ${analyzed.reminder.title}"
                newTodos.isNotEmpty() -> "Saved · added ${newTodos.size} to-do${if (newTodos.size > 1) "s" else ""}"
                analyzed.facts.isNotEmpty() -> "Saved · learned ${analyzed.facts.size} new thing${if (analyzed.facts.size > 1) "s" else ""} about you"
                else -> "Saved to ${analyzed.collection}"
            }
            isCapturing.value = false
        }
    }

    /** Reuse an existing thread name when the AI words it slightly differently. */
    private suspend fun matchThread(name: String): String {
        val existing = dao.getRecentMemories(300).map { it.threadKey }.distinct()
        val n = name.trim().lowercase()
        return existing.firstOrNull { it.lowercase() == n || it.lowercase().contains(n) || n.contains(it.lowercase()) && it.length > 3 }
            ?: name.trim().replaceFirstChar { it.uppercase() }
    }

    // ── Facts ────────────────────────────────────────────────────
    val facts: StateFlow<List<PersonalFactEntity>> = dao.observePersonalFacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addFact(text: String, category: String = "Profile") {
        if (text.isBlank()) return
        viewModelScope.launch { dao.insertFact(PersonalFactEntity(fact = text.trim(), category = category)) }
    }

    fun togglePin(fact: PersonalFactEntity) {
        viewModelScope.launch { dao.insertFact(fact.copy(pinned = !fact.pinned)) }
    }

    fun deleteFact(fact: PersonalFactEntity) {
        viewModelScope.launch { dao.deleteFact(fact) }
    }

    // ── People, places & projects ────────────────────────────────
    private val entityStore = com.keepsakeai.app.data.preferences.EntityStore(app)
    val entities = MutableStateFlow(entityStore.all())
    val openEntityKey = MutableStateFlow<String?>(null)
    val isBackfilling = MutableStateFlow(false)

    fun backfilledCount(): Int = entityStore.backfilled.size

    fun hideEntity(key: String) {
        entityStore.hide(key)
        entities.value = entityStore.all()
        openEntityKey.value = null
    }

    /** Finds people, places and projects in memories saved before this feature existed. */
    fun backfillEntities() {
        if (isBackfilling.value) return
        viewModelScope.launch {
            isBackfilling.value = true
            val done = entityStore.backfilled
            val todo = dao.getRecentMemories(300).filter { it.id !in done && it.source != CaptureSource.UPI_MESSAGE }.take(90)
            var found = 0
            todo.chunked(15).forEach { batch ->
                val result = app.mindPilotClient.extractEntities(batch)
                batch.forEach { m ->
                    result[m.id].orEmpty().forEach { e -> entityStore.addMention(e.type, e.name, e.note, m.id, m.createdAt); found++ }
                }
                entityStore.backfilled = entityStore.backfilled + batch.map { it.id }
                entities.value = entityStore.all()
            }
            isBackfilling.value = false
            statusMessage.value = if (todo.isEmpty()) "All memories already scanned" else "Found $found mentions in ${todo.size} memories"
        }
    }

    // ── To-dos ───────────────────────────────────────────────────
    private val todoStore = com.keepsakeai.app.data.preferences.TodoStore(app)
    val todos = MutableStateFlow(todoStore.all())

    fun addTodo(text: String, due: Long? = null) {
        todoStore.add(text, due)
        todos.value = todoStore.all()
    }

    fun toggleTodo(id: Long) {
        todoStore.toggle(id)
        todos.value = todoStore.all()
    }

    fun deleteTodo(id: Long) {
        todoStore.remove(id)
        todos.value = todoStore.all()
    }

    fun setTodoDue(id: Long, due: Long?) {
        todoStore.update(id) { it.copy(due = due) }
        todos.value = todoStore.all()
    }

    /** Turns a dated to-do into a reminder (day before and on the day, 9am). */
    fun remindTodo(t: com.keepsakeai.app.data.preferences.Todo) {
        val due = t.due ?: return
        val r = KeepReminder(id = t.id, title = t.text, atMillis = due, kind = "due", memoryId = t.memoryId ?: 0L, hasTime = false)
        reminderStore.add(r)
        scheduleReminder(r)
    }

    // ── Morning brief & voice journal ────────────────────────────
    private val nudgePrefs = com.keepsakeai.app.data.preferences.NudgePrefs(app)
    fun markViewed(id: Long) = nudgePrefs.markViewed(id)

    /** Asks the home hub to switch view (e.g. "TODOS" from the brief). */
    val hubRequest = MutableStateFlow<String?>(null)

    /** idle → transcribing → saved(transcript) | failed(message) */
    val journalState = MutableStateFlow<String>("idle")
    val journalTranscript = MutableStateFlow<String?>(null)

    fun saveJournal(file: java.io.File) {
        viewModelScope.launch {
            journalState.value = "transcribing"
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes == null || bytes.size < 2000) {
                journalState.value = "failed:That was too short to hear anything."
                return@launch
            }
            if (app.apiKeyStore.geminiKey.isBlank()) {
                journalState.value = "failed:Add your Gemini key in Settings to use the voice journal."
                return@launch
            }
            val text = app.mindPilotClient.transcribeAudio(bytes)
            if (text == null) {
                journalState.value = "failed:Couldn't make that out. Try again somewhere quieter?"
                return@launch
            }
            val date = java.text.SimpleDateFormat("EEEE d MMMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date())
            journalTranscript.value = text
            captureText("Voice journal · $date\n\n$text", CaptureSource.VOICE_MEMO)
            journalState.value = "saved"
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    fun resetJournal() {
        journalState.value = "idle"
        journalTranscript.value = null
    }

    // ── Pull to refresh ──────────────────────────────────────────
    val isRefreshing = MutableStateFlow(false)

    fun refreshAll(scanSms: Boolean = false) {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            val started = System.currentTimeMillis()
            reminders.value = reminderStore.all()
            todos.value = todoStore.all()
            entities.value = entityStore.all()
            refreshPlace()
            if (scanSms && androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) scanSmsInboxForUpi(7)
            refreshWidget()
            // Let the spinner be seen; refreshing that ends instantly feels broken.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 650) kotlinx.coroutines.delay(650 - elapsed)
            isRefreshing.value = false
        }
    }

    // ── Reminders found in captures ──────────────────────────────
    private val reminderStore = com.keepsakeai.app.data.preferences.ReminderStore(app)
    val reminders = MutableStateFlow(reminderStore.all())

    fun scheduleReminder(r: KeepReminder) {
        val at = com.keepsakeai.app.worker.ReminderWorker.schedule(app, r).first()
        reminderStore.update(r.id) { it.copy(status = "scheduled", notifyAt = at) }
        reminders.value = reminderStore.all()
        statusMessage.value = "I'll remind you " + java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at))
    }

    fun dismissReminder(r: KeepReminder) {
        com.keepsakeai.app.worker.ReminderWorker.cancel(app, r.id)
        reminderStore.update(r.id) { it.copy(status = "dismissed") }
        reminders.value = reminderStore.all()
    }

    fun captureImageUri(uri: Uri, source: CaptureSource = CaptureSource.CAMERA_SCAN) {
        viewModelScope.launch {
            isCapturing.value = true
            val local = withContext(Dispatchers.IO) { com.keepsakeai.app.capture.ImagePrep.importToSnaps(app, uri) } ?: uri
            val ocrText = runCatching { app.ocrProcessor.extractTextFromUri(local) }.getOrDefault("")
            // Even with no readable (Latin) text, Gemini can still read the photo itself.
            captureText(ocrText.ifBlank { "Photo" }, source, photoUri = local.toString())
            isCapturing.value = false
        }
    }

    /** Set by the widget / app shortcut so snapsake opens the camera straight away. */
    val autoCamera = MutableStateFlow(false)

    fun addManualExpense(merchant: String, amount: Double, category: String) {
        viewModelScope.launch {
            dao.insertExpense(
                ExpenseEntity(
                    merchant = merchant.ifBlank { "Manual Expense" },
                    amount = amount,
                    currency = app.apiKeyStore.defaultCurrency,
                    category = category,
                    sourceMethod = "Manual"
                )
            )
            refreshWidget()
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            dao.deleteMemory(memory)
            entityStore.forgetMemory(memory.id)
            entities.value = entityStore.all()
            runCatching { MindSpaceWidget().updateAll(app) }
        }
    }

    fun toggleProvider(provider: AiProvider) {
        val current = selectedProviders.value.toMutableSet()
        if (provider in current && current.size > 1) current.remove(provider)
        else current.add(provider)
        selectedProviders.value = current
    }

    fun runMindPilot(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isAskingPilot.value = true
            val contextMemories = if (useMemoryContext.value) {
                val all = dao.getRecentMemories(100)
                val topSemantic = app.vectorSearch.rankMemories(query, all, topK = 8).map { it.memory }
                topSemantic.ifEmpty { all.take(10) }
            } else {
                emptyList()
            }
            val used = contextMemories.take(12)
            pilotSources.value = used.associateBy { it.id }
            pilotResponses.value = app.mindPilotClient.askMindPilot(
                query = query,
                providers = selectedProviders.value,
                memories = contextMemories,
                agentMode = agentModeEnabled.value,
                deviceContext = DeviceContext.promptBlock(app),
                todosContext = todoStore.all().filter { !it.done }.take(30).joinToString("\n") { t ->
                    "- ${t.text}" + (t.due?.let { d -> " (due " + java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.ENGLISH).format(java.util.Date(d)) + ")" } ?: "")
                },
                factsContext = dao.getFacts().sortedByDescending { it.pinned }.take(25)
                    .joinToString("\n") { "- ${it.fact}${if (it.pinned) " (important)" else ""}" },
                moneyContext = withContext(Dispatchers.Default) {
                    spendDigest(
                        expenses.value.ifEmpty { dao.observeAllExpenses().first() },
                        com.keepsakeai.app.data.preferences.MoneyPrefs(app).budgets,
                        includeRows = isMoneyQuestion(query)
                    )
                }
            )
            pilotResponses.value = pilotResponses.value.map { r ->
                if (r.isError) r else r.copy(
                    cited = Regex("""\[(\d{1,2})]""").findAll(r.content)
                        .mapNotNull { m -> used.getOrNull(m.groupValues[1].toInt() - 1)?.id }
                        .distinct().toList()
                )
            }
            isAskingPilot.value = false
        }
    }


    fun approvePendingUpi(item: PendingUpiTransactionEntity, categoryOverride: String = item.suggestedCategory) {
        viewModelScope.launch {
            dao.insertExpense(
                ExpenseEntity(
                    merchant = item.merchant,
                    amount = item.amount,
                    currency = item.currency,
                    category = categoryOverride,
                    notes = item.utrRef?.let { "UTR: $it" } ?: item.bankOrApp,
                    sourceMethod = if (" · " in item.bankOrApp) item.bankOrApp else "UPI · ${item.bankOrApp}",
                    timestamp = item.receivedAt
                )
            )
            val embed = app.vectorSearch.embedText("UPI receipt ${item.merchant} ${item.currency} ${item.amount} $categoryOverride ${item.rawMessage}")
            dao.insertMemory(
                MemoryEntity(
                    title = "${item.merchant} — ${item.currency} ${item.amount}",
                    rawContent = item.rawMessage,
                    aiSummary = "Approved payment via ${item.bankOrApp} ($categoryOverride).",
                    collectionName = "Receipts",
                    source = CaptureSource.UPI_MESSAGE,
                    tagsCsv = "upi,receipt,${categoryOverride.lowercase()}",
                    embeddingCsv = app.vectorSearch.encodeVector(embed),
                    createdAt = item.receivedAt
                )
            )
            dao.deletePendingUpi(item)
            refreshWidget()
        }
    }

    fun dismissPendingUpi(item: PendingUpiTransactionEntity) {
        rememberDismissed(item)
        viewModelScope.launch { dao.deletePendingUpi(item); refreshWidget() }
    }

    fun approveAllPendingUpi() {
        viewModelScope.launch {
            pendingUpi.value.forEach { approvePendingUpi(it) }
        }
    }

    val isScanningSms = MutableStateFlow(false)

    /** Replays the SMS inbox for the last [days] days into the Pending approval queue, skipping anything already seen. */
    fun scanSmsInboxForUpi(days: Int = 30) {
        if (isScanningSms.value) return
        viewModelScope.launch {
            isScanningSms.value = true
            val found = withContext(Dispatchers.IO) { UpiSmsInboxScanner.scanRecentInbox(app, days) }
            val dismissed = dismissedUpiKeys()
            var added = 0
            val fresh = mutableListOf<PendingUpiTransactionEntity>()
            found.forEach { item ->
                val key = upiKey(item)
                val seen = key in dismissed || if (item.utrRef != null) {
                    dao.countPendingByUtr(item.utrRef) > 0 || dao.countExpensesByNote("UTR: ${item.utrRef}") > 0
                } else {
                    val from = item.receivedAt - 5 * 60_000
                    val to = item.receivedAt + 5 * 60_000
                    dao.countPendingByAmountWindow(item.amount, from, to) > 0 ||
                        dao.countExpensesByAmountWindow(item.amount, from, to) > 0
                }
                if (!seen) fresh.add(item)
            }
            // Let AI sort payees the keyword rules couldn't place.
            val unknown = fresh.filter { it.suggestedCategory == "Other" && !it.merchant.endsWith(" payment") }
                .map { it.merchant }.distinct().take(80)
            val aiCats = if (unknown.isEmpty()) emptyMap() else app.mindPilotClient.categorizeMerchants(unknown)
            fresh.forEach { item ->
                dao.insertPendingUpi(aiCats[item.merchant]?.let { item.copy(suggestedCategory = it) } ?: item)
                added++
            }
            isScanningSms.value = false
            refreshWidget()
            statusMessage.value = when {
                found.isEmpty() -> "No UPI payments found in the last $days days"
                added == 0 -> "All ${found.size} payments already imported"
                else -> "Found $added new payment${if (added > 1) "s" else ""} to review"
            }
        }
    }

    val importedCount: StateFlow<Int> = dao.observeImportedCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /** Removes every payment that came from messages (approved + waiting) so a clean re-import is possible. */
    fun removeImportedPayments() {
        viewModelScope.launch {
            val n = dao.deleteImportedExpenses()
            dao.deleteUpiMemories()
            dao.clearPendingUpi()
            app.getSharedPreferences("upi_prefs", android.content.Context.MODE_PRIVATE).edit().remove("dismissed").apply()
            runCatching { MindSpaceWidget().updateAll(app) }
            statusMessage.value = "Removed $n imported payment${if (n == 1) "" else "s"}"
        }
    }

    fun clearPendingUpi() {
        viewModelScope.launch {
            pendingUpi.value.forEach { rememberDismissed(it) }
            dao.clearPendingUpi()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            dao.deleteExpense(expense)
            statusMessage.value = "Deleted ${expense.merchant}"
            refreshWidget()
        }
    }

    fun refreshWidget() {
        viewModelScope.launch {
            runCatching { MindSpaceWidget().updateAll(app) }
            runCatching { com.keepsakeai.app.notify.MoneyWatch.check(app) }
        }
    }

    private fun upiKey(item: PendingUpiTransactionEntity) = item.utrRef ?: "${item.amount}@${item.receivedAt}"

    private fun dismissedUpiKeys(): Set<String> =
        app.getSharedPreferences("upi_prefs", android.content.Context.MODE_PRIVATE)
            .getStringSet("dismissed", emptySet()).orEmpty()

    private fun rememberDismissed(item: PendingUpiTransactionEntity) {
        val prefs = app.getSharedPreferences("upi_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("dismissed", dismissedUpiKeys() + upiKey(item)).apply()
    }

    fun refreshDailySuggest() {
        viewModelScope.launch {
            val recent = dao.getRecentMemories(10)
            dao.clearSuggestions()
            dao.insertSuggestion(
                DigestSuggestionEntity(
                    headline = "Mind Suggest Daily Brief",
                    body = if (recent.isEmpty()) "Capture a note, link, or receipt to activate daily insights."
                    else "Active across ${recent.map { it.collectionName }.distinct().joinToString(", ")}.",
                    actionLabel = "Ask Mind Pilot"
                )
            )
            runCatching { MindSpaceWidget().updateAll(app) }
        }
    }
}
