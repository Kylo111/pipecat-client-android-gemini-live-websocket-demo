package ai.pipecat.gemini_multimodal_websocket_demo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ConversationDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.DocumentDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.SessionDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.TaskRecordDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.ReasoningResultDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.DocumentEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity
import ai.pipecat.gemini_multimodal_websocket_demo.models.TaskRecord
import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningResult

@Database(
    entities = [
        ConversationEntity::class,
        SessionEntity::class,
        DocumentEntity::class,
        TaskRecord::class,
        ReasoningResult::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    abstract fun sessionDao(): SessionDao
    abstract fun documentDao(): DocumentDao
    abstract fun taskRecordDao(): TaskRecordDao
    abstract fun reasoningResultDao(): ReasoningResultDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Migration from version 3 to 4: Add template tracking fields
         * Adds origin_template_id and origin_template_version columns to conversations table
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add origin_template_id column (nullable String)
                database.execSQL(
                    "ALTER TABLE conversations ADD COLUMN origin_template_id TEXT DEFAULT NULL"
                )
                
                // Add origin_template_version column (nullable Int)
                database.execSQL(
                    "ALTER TABLE conversations ADD COLUMN origin_template_version INTEGER DEFAULT NULL"
                )
            }
        }
        
        /**
         * Migration from version 4 to 5: Add Reasoning Coordination tables
         * Adds reasoning_tasks and reasoning_results tables for task deduplication
         * and persistent result storage.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create reasoning_tasks table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reasoning_tasks (
                        taskId TEXT PRIMARY KEY NOT NULL,
                        conversationId TEXT NOT NULL,
                        taskDescription TEXT NOT NULL,
                        topics TEXT NOT NULL,
                        topicFingerprint TEXT NOT NULL,
                        status TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        resultSummary TEXT,
                        errorMessage TEXT
                    )
                """)
                
                // Create index on conversationId and createdAt for deduplication queries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_reasoning_tasks_conversation_created 
                    ON reasoning_tasks(conversationId, createdAt)
                """)
                
                // Create index on topicFingerprint for quick lookup
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_reasoning_tasks_fingerprint 
                    ON reasoning_tasks(topicFingerprint)
                """)
                
                // Create reasoning_results table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reasoning_results (
                        resultId TEXT PRIMARY KEY NOT NULL,
                        taskId TEXT NOT NULL,
                        conversationId TEXT NOT NULL,
                        resultType TEXT NOT NULL,
                        topics TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        keyFacts TEXT NOT NULL,
                        sources TEXT NOT NULL,
                        fullContent TEXT,
                        createdAt INTEGER NOT NULL,
                        consumedAt INTEGER,
                        consumedBy TEXT,
                        archived INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create index on conversationId for result queries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_reasoning_results_conversation 
                    ON reasoning_results(conversationId, archived, createdAt)
                """)
                
                // Create index on taskId for linking results to tasks
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_reasoning_results_task 
                    ON reasoning_results(taskId)
                """)
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_app_database"
                )
                    // Add migrations
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // Use destructive migration as fallback
                    // Safe since app is not yet released to users
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
