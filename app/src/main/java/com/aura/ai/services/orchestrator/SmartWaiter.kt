package com.aura.ai.services.orchestrator

import com.aura.ai.services.AppController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class SmartWaiter(
    private val appController: AppController,
    private val activeMonitor: ActiveMonitor,
    private val appProfileManager: AppProfileManager
) {
    
    private val appResponseTimes = mutableMapOf<String, MutableList<Long>>()
    
    suspend fun waitForScreenChange(
        previousText: String,
        timeoutMs: Long = 120000,
        appPackage: String? = null
    ): String {
        val adaptiveTimeout = if (appPackage != null) {
            getAdaptiveTimeout(appPackage, timeoutMs)
        } else timeoutMs
        
        return withTimeoutOrNull(adaptiveTimeout) {
            var lastText = previousText
            var stableCount = 0
            
            while (true) {
                delay(getPollInterval(appPackage))
                val currentText = appController.readScreen()
                
                activeMonitor.checkForInterruptions()
                
                if (currentText != lastText) {
                    stableCount = 0
                    lastText = currentText
                } else if (currentText.isNotEmpty()) {
                    stableCount++
                    if (stableCount >= 3) {
                        return@withTimeoutOrNull currentText
                    }
                }
                
                if (currentText.contains("Copy") || currentText.contains("Regenerate") ||
                    currentText.contains("👍") || currentText.contains("Thumbs up")) {
                    return@withTimeoutOrNull currentText
                }
            }
        } ?: appController.readScreen()
    }
    
    suspend fun waitForText(
        text: String,
        timeoutMs: Long = 30000
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val screenText = appController.readScreen()
                if (screenText.contains(text, ignoreCase = true)) return@withTimeoutOrNull true
                delay(1000)
            }
        } ?: false
    }
    
    suspend fun waitForApp(
        packageName: String,
        timeoutMs: Long = 10000
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (appController.isAppOpen(packageName)) {
                    recordAppResponseTime(packageName, timeoutMs)
                    return@withTimeoutOrNull true
                }
                delay(500)
            }
        } ?: false
    }
    
    suspend fun waitForCondition(
        condition: suspend () -> Boolean,
        timeoutMs: Long = 30000,
        pollIntervalMs: Long = 1000
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (condition()) return@withTimeoutOrNull true
                delay(pollIntervalMs)
            }
        } ?: false
    }
    
    suspend fun adaptiveWait(
        appPackage: String,
        timeoutMs: Long = 120000
    ): String {
        val previousText = appController.readScreen()
        return waitForScreenChange(previousText, timeoutMs, appPackage)
    }
    
    private fun getAdaptiveTimeout(appPackage: String, defaultTimeout: Long): Long {
        val history = appResponseTimes[appPackage]
        if (history != null && history.isNotEmpty()) {
            val avg = history.average().toLong()
            val withBuffer = (avg * 1.5).toLong()
            return withBuffer.coerceIn(defaultTimeout / 2, defaultTimeout * 2)
        }
        return defaultTimeout
    }
    
    private fun getPollInterval(appPackage: String?): Long {
        if (appPackage == null) return 3000
        val profile = appProfileManager.getProfile(appPackage)
        return profile?.avgResponseTime?.div(10)?.coerceIn(1000, 5000) ?: 3000
    }
    
    private fun recordAppResponseTime(appPackage: String, timeoutUsed: Long) {
        appResponseTimes.getOrPut(appPackage) { mutableListOf() }.add(timeoutUsed)
        if (appResponseTimes[appPackage]!!.size > 20) {
            appResponseTimes[appPackage]!!.removeAt(0)
        }
    }
    
    fun getAverageResponseTime(appPackage: String): Long {
        val history = appResponseTimes[appPackage] ?: return 0
        return if (history.isEmpty()) 0 else history.average().toLong()
    }
}
