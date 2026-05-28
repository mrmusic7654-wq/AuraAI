package com.aura.ai.services.intelligence

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ServiceStatus(
    val name: String,
    val isHealthy: Boolean,
    val lastCheck: Long,
    val responseTimeMs: Long,
    val errorMessage: String = ""
)

class ServiceHealthMonitor(
    private val checkGitHub: suspend () -> Boolean,
    private val checkGemini: suspend () -> Boolean,
    private val checkDeepSeek: suspend () -> Boolean,
    private val checkNetwork: suspend () -> Boolean
) {
    
    private val _services = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())
    val services: StateFlow<Map<String, ServiceStatus>> = _services.asStateFlow()
    
    private var monitorJob: Job? = null
    
    fun startMonitoring(intervalMs: Long = 60000) {
        monitorJob?.cancel()
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                checkAllServices()
                delay(intervalMs)
            }
        }
    }
    
    fun stopMonitoring() {
        monitorJob?.cancel()
    }
    
    suspend fun checkAllServices() {
        val statuses = mutableMapOf<String, ServiceStatus>()
        
        statuses["github"] = checkService("GitHub API", checkGitHub)
        statuses["gemini"] = checkService("Gemini API", checkGemini)
        statuses["deepseek"] = checkService("DeepSeek App", checkDeepSeek)
        statuses["network"] = checkService("Network", checkNetwork)
        
        _services.value = statuses
    }
    
    private suspend fun checkService(name: String, checker: suspend () -> Boolean): ServiceStatus {
        val startTime = System.currentTimeMillis()
        return try {
            withTimeout(10000) {
                val healthy = checker()
                ServiceStatus(name, healthy, System.currentTimeMillis(), System.currentTimeMillis() - startTime)
            }
        } catch (e: Exception) {
            ServiceStatus(name, false, System.currentTimeMillis(), 0, e.message ?: "Unknown error")
        }
    }
    
    fun isServiceHealthy(name: String): Boolean {
        return _services.value[name]?.isHealthy ?: false
    }
    
    fun getAllHealthy(): Boolean {
        return _services.value.values.all { it.isHealthy }
    }
    
    fun getUnhealthyServices(): List<String> {
        return _services.value.filter { !it.value.isHealthy }.map { it.key }
    }
    
    fun getHealthReport(): String {
        val sb = StringBuilder("🏥 SERVICE HEALTH:\n\n")
        _services.value.forEach { (name, status) ->
            val icon = if (status.isHealthy) "✅" else "❌"
            sb.append("$icon $name: ${if (status.isHealthy) "${status.responseTimeMs}ms" else status.errorMessage}\n")
        }
        return sb.toString()
    }
                      }
