package com.aura.ai.domain.repository

import com.aura.ai.data.local.entities.AppUsageEntity
import kotlinx.coroutines.flow.Flow

interface AnalyticsRepository {
    
    suspend fun logAppUsage(packageName: String, appName: String, duration: Long)
    
    fun getRecentUsage(limit: Int = 100): Flow<List<AppUsageEntity>>
    
    fun getUsageForApp(packageName: String): Flow<List<AppUsageEntity>>
    
    suspend fun deleteOldUsage(beforeTimestamp: Long)
    
    suspend fun getMostUsedApps(limit: Int = 10): List<Pair<String, Long>>
}
