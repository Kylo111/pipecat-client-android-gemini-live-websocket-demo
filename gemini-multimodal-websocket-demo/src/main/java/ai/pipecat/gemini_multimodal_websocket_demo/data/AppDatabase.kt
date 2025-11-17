package ai.pipecat.gemini_multimodal_websocket_demo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    abstract fun sessionDao(): SessionDao
    abstract fun documentDao(): DocumentDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
