package com.aura.ai.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================
// ENTITIES (Inline - no separate files)
// ============================================

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val selectedModel: String = "gemini-3.1-flash-lite"
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_usage")
data class ModelUsageEntity(
    @PrimaryKey val modelName: String,
    val dailyRequests: Int = 0,
    val dailyLimit: Int = 1500,
    val lastResetDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
    val cooldownUntil: Long = 0,
    val strength: String = ""
)

// ============================================
// DAOs (Inline - no separate files)
// ============================================

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastActiveAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET lastActiveAt = :time, title = :title WHERE id = :sessionId")
    suspend fun updateSession(sessionId: String, time: Long, title: String)
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("UPDATE sessions SET selectedModel = :model WHERE id = :sessionId")
    suspend fun updateSelectedModel(sessionId: String, model: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionOnce(sessionId: String): List<MessageEntity>
    
    @Insert
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)
}

@Dao
interface ModelUsageDao {
    @Query("SELECT * FROM model_usage WHERE modelName = :modelName")
    suspend fun getModelUsage(modelName: String): ModelUsageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateModelUsage(usage: ModelUsageEntity)
    
    @Query("SELECT * FROM model_usage ORDER BY dailyRequests DESC")
    fun getAllModelUsage(): Flow<List<ModelUsageEntity>>
    
    @Query("UPDATE model_usage SET dailyRequests = 0, lastResetDate = :date WHERE lastResetDate != :date")
    suspend fun resetDailyCounters(date: String)
}

// ============================================
// DATABASE (Single class - Hilt module handles creation)
// ============================================

@Database(
    entities = [SessionEntity::class, MessageEntity::class, ModelUsageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun modelUsageDao(): ModelUsageDao
    
    companion object {
        @Volatile
        private var INSTANCE: SessionDatabase? = null
        
        fun getInstance(context: android.content.Context): SessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SessionDatabase::class.java,
                    "aura_sessions.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
