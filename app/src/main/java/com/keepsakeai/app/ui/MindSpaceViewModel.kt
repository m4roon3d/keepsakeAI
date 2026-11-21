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
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import com.keepsakeai.app.widget.MindSpaceWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun captureText(text: String, source: CaptureSource = CaptureSource.TEXT_NOTE, url: String? = null) {
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
                    embeddingCsv = app.vectorSearch.encodeVector(vector)
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
                agentMode = agentModeEnabled.value
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
                    sourceMethod = "UPI (${item.bankOrApp})",
                    timestamp = item.receivedAt
                )
            )
            val embed = app.vectorSearch.embedText("UPI receipt ${item.merchant} ${item.currency} ${item.amount} $categoryOverride ${item.rawMessage}")
            dao.insertMemory(
                MemoryEntity(
                    title = "${item.merchant} — ${item.currency} ${item.amount}",
                    rawContent = item.rawMessage,
                    aiSummary = "Approved UPI payment via ${item.bankOrApp} ($categoryOverride).",
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
        viewModelScope.launch { dao.deletePendingUpi(item) }
    }

    fun approveAllPendingUpi() {
        viewModelScope.launch {
            pendingUpi.value.forEach { approvePendingUpi(it) }
        }
    }

    fun scanSmsInboxForUpi() {
        viewModelScope.launch {
            val found = UpiSmsInboxScanner.scanRecentInbox(app)
            found.forEach { item ->
                if (item.utrRef == null || dao.countPendingByUtr(item.utrRef) == 0) {
                    dao.insertPendingUpi(item)
                }
            }
            statusMessage.value = "Queued ${found.size} UPI messages for review"
        }
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
