package com.aura.ai.data.repository

import com.aura.ai.data.local.dao.AppUsageDao
import com.aura.ai.data.local.entities.AppUsageEntity
import com.aura.ai.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val appUsageDao: AppUsageDao
) : AnalyticsRepository {
    
    override suspend fun logAppUsage(packageName: String, appName: String, duration: Long) {
        val usage = AppUsageEntity(
            packageName = packageName,
            appName = appName,
            timestamp = System.currentTimeMillis(),
            duration = duration
        )
        appUsageDao.insertUsage(usage)
    }
    
    override fun getRecentUsage(limit: Int): Flow<List<AppUsageEntity>> {
        return appUsageDao.getRecentUsage()
    }
    
    override fun getUsageForApp(packageName: String): Flow<List<AppUsageEntity>> {
        return appUsageDao.getUsageForApp(packageName)
    }
    
    override suspend fun deleteOldUsage(beforeTimestamp: Long) {
        appUsageDao.deleteOldUsage(beforeTimestamp)
    }
    
    override suspend fun getMostUsedApps(limit: Int): List<Pair<String, Long>> {
        // Implementation would aggregate usage by package
        return emptyList()
    }
}
