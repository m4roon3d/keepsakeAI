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

/** 7 → 8: places visited. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS place_visits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "lat REAL NOT NULL, lng REAL NOT NULL, arrivedAt INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, " +
                "name TEXT, confirmed INTEGER NOT NULL DEFAULT 0)"
        )
    }
}

/** 8 → 9: where a memory was kept, for "near here" recall. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memories ADD COLUMN lat REAL")
        db.execSQL("ALTER TABLE memories ADD COLUMN lng REAL")
    }
}

/** 9 → 10: places visited was removed, so its table (and the stays in it) is dropped. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS place_visits")
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
    version = 10,
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
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
