package com.aura.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aura.ai.data.local.entities.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AppUsageEntity)
    
    @Query("SELECT * FROM app_usage ORDER BY timestamp DESC LIMIT 100")
    fun getRecentUsage(): Flow<List<AppUsageEntity>>
    
    @Query("SELECT * FROM app_usage WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getUsageForApp(packageName: String): Flow<List<AppUsageEntity>>
    
    @Query("DELETE FROM app_usage WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldUsage(beforeTimestamp: Long)
}
