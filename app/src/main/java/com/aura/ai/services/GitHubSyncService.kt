package com.aura.ai.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aura.ai.domain.repository.GitHubRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GitHubSyncService : Service() {
    
    @Inject
    lateinit var gitHubRepository: GitHubRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncJob = serviceScope.launch {
            try {
                syncRepositories()
            } catch (e: Exception) {
                Timber.e(e, "GitHub sync failed")
            }
        }
        return START_NOT_STICKY
    }
    
    private suspend fun syncRepositories() {
        val result = gitHubRepository.listRepositories()
        result.onSuccess { repos ->
            Timber.d("Synced ${repos.size} repositories")
        }.onFailure { error ->
            Timber.e(error, "Failed to sync repositories")
        }
    }
    
    override fun onDestroy() {
        syncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
