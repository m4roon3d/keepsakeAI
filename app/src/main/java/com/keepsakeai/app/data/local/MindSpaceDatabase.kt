package com.keepsakeai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
        PersonalFactEntity::class
    ],
    version = 6,
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
