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
import kotlinx.coroutines.flow.map
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

    private val pipeline = com.keepsakeai.app.capture.CapturePipeline(app)

    fun captureText(text: String, source: CaptureSource = CaptureSource.TEXT_NOTE, url: String? = null, photoUri: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch { saveCapture(text, source, url, photoUri) }
    }

    private suspend fun saveCapture(
        text: String, source: CaptureSource, url: String? = null, photoUri: String? = null,
        geo: com.keepsakeai.app.capture.GeoTag? = null, quiet: Boolean = false,
    ): Boolean {
        isCapturing.value = true
        val saved = runCatching { pipeline.save(text, source, url, photoUri, geo = geo) }.getOrNull()
        isCapturing.value = false
        if (saved == null) {
            statusMessage.value = "Couldn't save that. Try again."
            return false
        }
        val analyzed = saved.analyzed
        entities.value = entityStore.all()
        todos.value = todoStore.all()
        reminders.value = reminderStore.all()
        refreshWidget()
        if (!quiet) statusMessage.value = when {
            analyzed.reminder != null -> "Saved · found a date: ${analyzed.reminder.title}"
            saved.todosAdded > 0 -> "Saved · added ${saved.todosAdded} to-do${if (saved.todosAdded > 1) "s" else ""}"
            analyzed.facts.isNotEmpty() -> "Saved · learned ${analyzed.facts.size} new thing${if (analyzed.facts.size > 1) "s" else ""} about you"
            else -> "Saved to ${analyzed.collection}"
        }
        return true
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
            refreshCalendar()
            if (emailStore.all().isNotEmpty()) com.keepsakeai.app.worker.EmailSyncWorker.runNow(app)
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

    fun captureImageUri(uri: Uri, source: CaptureSource = CaptureSource.CAMERA_SCAN) = captureMediaUris(listOf(uri), source)

    /** Photos and videos from the gallery or share sheet, one after another (keeps Gemini under its rate limit). */
    fun captureMediaUris(uris: List<Uri>, source: CaptureSource = CaptureSource.CAMERA_SCAN) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            uris.forEachIndexed { i, uri ->
                if (uris.size > 1) statusMessage.value = "Saving ${i + 1} of ${uris.size}…"
                if (saveMedia(uri, source, quiet = uris.size > 1)) ok++
            }
            if (uris.size > 1) statusMessage.value = "Saved $ok of ${uris.size}"
        }
    }

    private suspend fun saveMedia(uri: Uri, source: CaptureSource, quiet: Boolean): Boolean {
        val type = runCatching { app.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (type.startsWith("video/")) {
            isCapturing.value = true
            val (tag, durationMs) = withContext(Dispatchers.IO) { com.keepsakeai.app.capture.MediaMeta.readVideo(app, uri) }
            val strip = withContext(Dispatchers.IO) { com.keepsakeai.app.capture.MediaMeta.videoStrip(app, uri) }
            if (strip == null) {
                isCapturing.value = false
                statusMessage.value = "Couldn't read that video."
                return false
            }
            val length = durationMs?.let { val sec = it / 1000; " · %d:%02d".format(sec / 60, sec % 60) }.orEmpty()
            return saveCapture("Video$length (the photo shows three frames: start, middle and end)", source,
                photoUri = Uri.fromFile(strip).toString(), geo = tag, quiet = quiet)
        }
        isCapturing.value = true
        val local = withContext(Dispatchers.IO) { com.keepsakeai.app.capture.ImagePrep.importToSnaps(app, uri) } ?: uri
        val ocrText = runCatching { app.ocrProcessor.extractTextFromUri(local) }.getOrDefault("")
        // Even with no readable (Latin) text, Gemini can still read the photo itself.
        return saveCapture(ocrText.ifBlank { "Photo" }, source, photoUri = local.toString(), quiet = quiet)
    }

    /** Set by the widget / app shortcut so lens opens the camera straight away. */
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
                deviceContext = DeviceContext.promptBlock(app) + "\n" +
                    withContext(Dispatchers.IO) { com.keepsakeai.app.calendar.CalendarReader.promptBlock(app) },
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

    // ── Phone calendar (read-only) ───────────────────────────────
    /** Events from today to two weeks ahead, for the brief, the Today card and the home screen. */
    val calendarEvents = MutableStateFlow<List<com.keepsakeai.app.calendar.CalEvent>>(emptyList())

    fun refreshCalendar() {
        viewModelScope.launch {
            val start = com.keepsakeai.app.ui.spend.startOfDay(System.currentTimeMillis())
            calendarEvents.value = withContext(Dispatchers.IO) {
                com.keepsakeai.app.calendar.CalendarReader.events(app, start, start + 14L * 24 * 60 * 60 * 1000)
            }
        }
    }

    init { refreshCalendar() }

    // ── Email (receipts, orders, tickets, bills) ─────────────────
    private val emailStore = com.keepsakeai.app.email.EmailStore(app)
    val emailAccounts = MutableStateFlow(emailStore.all())
    val emailSyncing: StateFlow<Boolean> = androidx.work.WorkManager.getInstance(app)
        .getWorkInfosByTagFlow(com.keepsakeai.app.worker.EmailSyncWorker.TAG)
        .map { list -> list.any { it.state == androidx.work.WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // When a sync finishes, show what it found.
        viewModelScope.launch {
            var was = false
            emailSyncing.collect { now ->
                if (was && !now) {
                    emailAccounts.value = emailStore.all()
                    todos.value = todoStore.all()
                    reminders.value = reminderStore.all()
                    entities.value = entityStore.all()
                }
                was = now
            }
        }
    }

    /** Checks the details with a real sign-in, then saves and starts the first read. Returns an error or null. */
    suspend fun connectEmail(address: String, password: String, provider: com.keepsakeai.app.email.MailProvider, host: String, port: Int): String? {
        val addr = address.trim().lowercase()
        val pw = password.replace(Regex("""\s"""), "")
        if (!addr.contains('@') || pw.isBlank()) return "Enter your email address and app password."
        if (host.isBlank()) return "Enter your mail server (IMAP host)."
        val err = withContext(Dispatchers.IO) {
            runCatching { com.keepsakeai.app.email.MailReader.test(host.trim(), port, addr, pw) }.exceptionOrNull()
        }
        if (err != null) return err.message?.takeIf { err is com.keepsakeai.app.email.MailAuthException }
            ?: "Couldn't connect to ${host.trim()}. Check the server and your internet."
        val id = addr.replace(Regex("[^a-z0-9]"), "_")
        app.apiKeyStore.setEmailPassword(id, pw)
        emailStore.upsert(com.keepsakeai.app.email.EmailAccount(id, addr, provider, host.trim(), port))
        emailAccounts.value = emailStore.all()
        com.keepsakeai.app.worker.EmailSyncWorker.schedule(app)
        com.keepsakeai.app.worker.EmailSyncWorker.runNow(app)
        return null
    }

    fun syncEmailNow() = com.keepsakeai.app.worker.EmailSyncWorker.runNow(app)

    fun disconnectEmail(id: String) {
        emailStore.remove(id)
        app.apiKeyStore.setEmailPassword(id, null)
        emailAccounts.value = emailStore.all()
        com.keepsakeai.app.worker.EmailSyncWorker.schedule(app)
    }

    /** Deletes every memory and payment that came from email. */
    suspend fun removeEmailImports(): Int {
        val n = dao.deleteEmailMemories()
        dao.deleteEmailExpenses()
        emailAccounts.value.forEach { a -> emailStore.update(a.id) { it.copy(kept = 0) } }
        emailAccounts.value = emailStore.all()
        refreshWidget()
        return n
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
