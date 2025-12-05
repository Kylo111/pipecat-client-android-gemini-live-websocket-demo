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
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ConversationEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.DocumentEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.SessionEntity

@Database(
    entities = [
        ConversationEntity::class,
        SessionEntity::class,
        DocumentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    abstract fun sessionDao(): SessionDao
    abstract fun documentDao(): DocumentDao
    
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
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_app_database"
                )
                    // Add migration for version 3 to 4
                    .addMigrations(MIGRATION_3_4)
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
