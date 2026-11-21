package com.keepsakeai.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MindSpaceDao {

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE collectionName = :collection ORDER BY createdAt DESC")
    fun observeMemoriesByCollection(collection: String): Flow<List<MemoryEntity>>

    @Query("SELECT DISTINCT collectionName FROM memories ORDER BY collectionName ASC")
    fun observeCollections(): Flow<List<String>>

    @Query("""
        SELECT * FROM memories 
        WHERE title LIKE '%' || :query || '%' 
           OR rawContent LIKE '%' || :query || '%' 
           OR aiSummary LIKE '%' || :query || '%' 
           OR tagsCsv LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 15): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // Pending UPI Preview & Approve Queue
    @Query("SELECT * FROM pending_upi_transactions ORDER BY receivedAt DESC")
    fun observePendingUpi(): Flow<List<PendingUpiTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_upi_transactions WHERE utrRef = :utr")
    suspend fun countPendingByUtr(utr: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE notes = :note")
    suspend fun countExpensesByNote(note: String): Int

    @Query("SELECT COUNT(*) FROM pending_upi_transactions WHERE amount = :amount AND receivedAt = :receivedAt")
    suspend fun countPendingByAmountTime(amount: Double, receivedAt: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND timestamp = :timestamp")
    suspend fun countExpensesByAmountTime(amount: Double, timestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingUpi(item: PendingUpiTransactionEntity): Long

    @Delete
    suspend fun deletePendingUpi(item: PendingUpiTransactionEntity)

    @Query("DELETE FROM pending_upi_transactions")
    suspend fun clearPendingUpi()

    // Daily Suggest
    @Query("SELECT * FROM daily_suggestions ORDER BY createdAt DESC LIMIT 5")
    fun observeLatestSuggestions(): Flow<List<DigestSuggestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: DigestSuggestionEntity): Long

    @Query("DELETE FROM daily_suggestions")
    suspend fun clearSuggestions()

    // AI Transparency Report Audit Log
    @Query("SELECT * FROM ai_call_audit ORDER BY timestamp DESC LIMIT 100")
    fun observeAiAuditLogs(): Flow<List<AiCallAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAuditLog(log: AiCallAuditEntity): Long

    @Query("DELETE FROM ai_call_audit")
    suspend fun clearAiAuditLogs()

    // Continuous Memory: Distilled Personal Facts & Threads
    @Query("SELECT * FROM personal_facts ORDER BY pinned DESC, createdAt DESC")
    fun observePersonalFacts(): Flow<List<PersonalFactEntity>>

    @Query("SELECT * FROM memories WHERE threadKey = :threadKey ORDER BY createdAt DESC")
    fun observeMemoriesByThread(threadKey: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalFact(fact: PersonalFactEntity): Long

    @Query("UPDATE personal_facts SET pinned = :pinned WHERE id = :id")
    suspend fun setFactPinned(id: Long, pinned: Boolean)

    @Delete
    suspend fun deletePersonalFact(fact: PersonalFactEntity)
}
