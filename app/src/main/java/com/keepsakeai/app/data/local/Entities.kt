package com.keepsakeai.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CaptureSource {
    TEXT_NOTE,
    SHARE_SHEET,
    SCREENSHOT_OCR,
    VOICE_MEMO,
    CAMERA_SCAN,
    LINK_CAPTURE,
    UPI_MESSAGE,
    EMAIL
}

/** What a money event is. Only EXPENSE is summed as spending. */
object TxnType {
    const val EXPENSE = "EXPENSE"
    const val TRANSFER = "TRANSFER"
    const val INCOME = "INCOME"
    const val INVESTMENT = "INVESTMENT"
    const val STATEMENT = "STATEMENT"
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawContent: String,
    val aiSummary: String,
    val collectionName: String,
    val source: CaptureSource,
    val sourceUrl: String? = null,
    val tagsCsv: String = "",
    val embeddingCsv: String = "",
    val threadKey: String = "General",
    val photoUrisCsv: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Double,
    val currency: String = "INR",
    val category: String,
    val notes: String = "",
    val sourceMethod: String = "Manual",
    val timestamp: Long = System.currentTimeMillis(),
    /** See [TxnType]. Only EXPENSE counts towards spending; transfers are kept but left out of totals. */
    val type: String = TxnType.EXPENSE
)

@Entity(tableName = "pending_upi_transactions")
data class PendingUpiTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Double,
    val currency: String = "INR",
    val suggestedCategory: String,
    val bankOrApp: String,
    val utrRef: String? = null,
    val rawMessage: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val type: String = TxnType.EXPENSE
)

/** Credit messages seen on this phone. Used only to spot a debit whose money landed in the user's own account. */
@Entity(tableName = "observed_credits")
data class ObservedCreditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val utrRef: String? = null,
    val account: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_suggestions")
data class DigestSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val headline: String,
    val body: String,
    val actionLabel: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_call_audit")
data class AiCallAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val feature: String,
    val promptPreview: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_facts")
data class PersonalFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val category: String,
    val pinned: Boolean = false,
    val sourceMemoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
