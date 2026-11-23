package com.keepsakeai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 6 → 7: transaction type on expenses and the review queue, plus the credits table. Keeps all existing data. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
        db.execSQL("ALTER TABLE pending_upi_transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS observed_credits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amount REAL NOT NULL, utrRef TEXT, account TEXT, receivedAt INTEGER NOT NULL)"
        )
    }
}

class Converters {
    @TypeConverter
    fun fromSource(value: CaptureSource): String = value.name

    @TypeConverter
    fun toSource(value: String): CaptureSource =
        runCatching { CaptureSource.valueOf(value) }.getOrDefault(CaptureSource.TEXT_NOTE)
}

@Database(
    entities = [
        MemoryEntity::class,
        ExpenseEntity::class,
        PendingUpiTransactionEntity::class,
        DigestSuggestionEntity::class,
        AiCallAuditEntity::class,
        PersonalFactEntity::class,
        ObservedCreditEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MindSpaceDatabase : RoomDatabase() {
    abstract fun dao(): MindSpaceDao

    companion object {
        @Volatile
        private var INSTANCE: MindSpaceDatabase? = null

        fun getInstance(context: Context): MindSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MindSpaceDatabase::class.java,
                    "keepsake_ai.db"
                )
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
