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
import com.keepsakeai.app.widget.MindSpaceWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val currentPlace = MutableStateFlow<String?>(null)

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
            val analyzed = app.mindPilotClient.analyzeCapture(text)
            val embedInput = "${analyzed.title} ${analyzed.summary} $text ${analyzed.tags.joinToString(" ")}"
            val vector = app.vectorSearch.embedText(embedInput)

            dao.insertMemory(
                MemoryEntity(
                    title = analyzed.title,
                    rawContent = text,
                    aiSummary = analyzed.summary,
                    collectionName = analyzed.collection,
                    source = source,
                    sourceUrl = url,
                    tagsCsv = analyzed.tags.joinToString(","),
                    embeddingCsv = app.vectorSearch.encodeVector(vector),
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
            runCatching { MindSpaceWidget().updateAll(app) }
            statusMessage.value = "Saved to ${analyzed.collection}"
            isCapturing.value = false
        }
    }

    fun captureImageUri(uri: Uri, source: CaptureSource = CaptureSource.CAMERA_SCAN) {
        viewModelScope.launch {
            isCapturing.value = true
            runCatching {
                val ocrText = app.ocrProcessor.extractTextFromUri(uri)
                if (ocrText.isBlank()) {
                    statusMessage.value = "No readable text found in image"
                } else {
                    captureText(ocrText, source)
                }
            }.onFailure {
                statusMessage.value = "OCR failed: ${it.localizedMessage}"
            }
            isCapturing.value = false
        }
    }

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
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            dao.deleteMemory(memory)
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
            pilotResponses.value = app.mindPilotClient.askMindPilot(
                query = query,
                providers = selectedProviders.value,
                memories = contextMemories,
                agentMode = agentModeEnabled.value,
                deviceContext = DeviceContext.promptBlock(app)
            )
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
            runCatching { MindSpaceWidget().updateAll(app) }
        }
    }

    fun dismissPendingUpi(item: PendingUpiTransactionEntity) {
        rememberDismissed(item)
        viewModelScope.launch { dao.deletePendingUpi(item) }
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
