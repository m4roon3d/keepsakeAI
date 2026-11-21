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

    @Query("SELECT * FROM memories WHERE id = :id")
    fun observeMemory(id: Long): Flow<MemoryEntity?>

    @androidx.room.Update
    suspend fun updateMemory(memory: MemoryEntity)

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

    @Query("SELECT COUNT(*) FROM pending_upi_transactions")
    suspend fun countPendingUpi(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE timestamp >= :since")
    suspend fun sumExpensesSince(since: Long): Double

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countMemories(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE notes = :note")
    suspend fun countExpensesByNote(note: String): Int

    @Query("SELECT COUNT(*) FROM pending_upi_transactions WHERE amount = :amount AND receivedAt BETWEEN :from AND :to")
    suspend fun countPendingByAmountWindow(amount: Double, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND timestamp BETWEEN :from AND :to")
    suspend fun countExpensesByAmountWindow(amount: Double, from: Long, to: Long): Int

    /** Expenses that came from UPI / bank messages (old "UPI (Bank)" and new "Channel · Bank" formats). */
    @Query("DELETE FROM expenses WHERE sourceMethod LIKE 'UPI (%' OR sourceMethod LIKE '% · %'")
    suspend fun deleteImportedExpenses(): Int

    @Query("DELETE FROM memories WHERE source = 'UPI_MESSAGE'")
    suspend fun deleteUpiMemories(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE sourceMethod LIKE 'UPI (%' OR sourceMethod LIKE '% · %'")
    fun observeImportedCount(): Flow<Int>

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

    @Query("SELECT * FROM personal_facts")
    suspend fun getFacts(): List<PersonalFactEntity>

    // Backup / restore
    @Query("SELECT * FROM memories")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM pending_upi_transactions")
    suspend fun getAllPending(): List<PendingUpiTransactionEntity>

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM personal_facts")
    suspend fun deleteAllFacts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(items: List<MemoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(items: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendings(items: List<PendingUpiTransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(items: List<PersonalFactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: PersonalFactEntity): Long

    @Delete
    suspend fun deleteFact(fact: PersonalFactEntity)

    @Query("SELECT * FROM memories WHERE threadKey = :threadKey ORDER BY createdAt DESC")
    fun observeMemoriesByThread(threadKey: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalFact(fact: PersonalFactEntity): Long

    @Query("UPDATE personal_facts SET pinned = :pinned WHERE id = :id")
    suspend fun setFactPinned(id: Long, pinned: Boolean)

    @Delete
    suspend fun deletePersonalFact(fact: PersonalFactEntity)
}
