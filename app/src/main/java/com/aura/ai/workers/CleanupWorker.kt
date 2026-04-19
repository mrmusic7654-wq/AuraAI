package com.aura.ai.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aura.ai.data.local.dao.AppUsageDao
import com.aura.ai.data.local.dao.TaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskDao: TaskDao,
    private val appUsageDao: AppUsageDao
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
            
            // Delete old app usage data
            appUsageDao.deleteOldUsage(thirtyDaysAgo)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
