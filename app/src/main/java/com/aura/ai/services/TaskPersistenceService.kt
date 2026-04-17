package com.aura.ai.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aura.ai.data.repository.AgentRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TaskPersistenceService : Service() {
    
    @Inject
    lateinit var agentRepository: AgentRepositoryImpl
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Auto-save task state periodically
        serviceScope.launch {
            while (isActive) {
                delay(10000) // Every 10 seconds
                autoSaveTaskState()
            }
        }
        return START_STICKY
    }
    
    private suspend fun autoSaveTaskState() {
        try {
            val activeTask = agentRepository.getActiveTask().value
            if (activeTask?.status == TaskStatus.EXECUTING) {
                agentRepository.pauseCurrentTask()
                Timber.d("Auto-saved task state: ${activeTask.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to auto-save task state")
        }
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
