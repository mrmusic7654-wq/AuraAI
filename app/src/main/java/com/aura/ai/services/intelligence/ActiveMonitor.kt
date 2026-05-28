package com.aura.ai.services.intelligence

import android.accessibilityservice.AccessibilityService
import com.aura.ai.services.AppController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MonitorEvent(
    val type: String, // "POPUP", "ANR", "PERMISSION", "UPDATE", "ERROR", "STUCK"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ActiveMonitor(
    private val appController: AppController,
    private val accessibilityService: AccessibilityService?
) {
    
    private val _events = MutableStateFlow<List<MonitorEvent>>(emptyList())
    val events: StateFlow<List<MonitorEvent>> = _events.asStateFlow()
    
    private var lastScreenText = ""
    private var stuckCounter = 0
    
    suspend fun checkForInterruptions(): List<MonitorEvent> {
        val newEvents = mutableListOf<MonitorEvent>()
        
        val currentText = try {
            appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
        } catch (e: Exception) { "" }
        
        // Check for stuck state
        if (currentText == lastScreenText && currentText.isNotEmpty()) {
            stuckCounter++
            if (stuckCounter >= 10) { // Same screen for 10 checks = probably stuck
                newEvents.add(MonitorEvent("STUCK", "Screen unchanged for extended period"))
                stuckCounter = 0
            }
        } else {
            stuckCounter = 0
        }
        
        // Check for popups
        val popupIndicators = listOf("Allow", "Deny", "OK", "Cancel", "Permission", "Update", "Later", "Install")
        for (indicator in popupIndicators) {
            if (currentText.contains(indicator, ignoreCase = true) && 
                !lastScreenText.contains(indicator, ignoreCase = true)) {
                newEvents.add(MonitorEvent("POPUP", "Detected popup with '$indicator'"))
                break
            }
        }
        
        // Check for ANR
        if (currentText.contains("isn't responding", ignoreCase = true) ||
            currentText.contains("ANR", ignoreCase = true)) {
            newEvents.add(MonitorEvent("ANR", "Application Not Responding detected"))
        }
        
        // Check for error dialogs
        if (currentText.contains("has stopped", ignoreCase = true) ||
            currentText.contains("keeps stopping", ignoreCase = true)) {
            newEvents.add(MonitorEvent("ERROR", "App crash detected"))
        }
        
        lastScreenText = currentText
        
        if (newEvents.isNotEmpty()) {
            _events.value = _events.value + newEvents
        }
        
        return newEvents
    }
    
    fun handlePopup(text: String): Boolean {
        return when {
            text.contains("Allow", ignoreCase = true) -> {
                try { appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("tap", "Allow"))); true }
                catch (e: Exception) { false }
            }
            text.contains("OK", ignoreCase = true) -> {
                try { appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("tap", "OK"))); true }
                catch (e: Exception) { false }
            }
            text.contains("Later", ignoreCase = true) -> {
                try { appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("tap", "Later"))); true }
                catch (e: Exception) { false }
            }
            else -> false
        }
    }
    
    fun getRecentEvents(limit: Int = 10): List<MonitorEvent> {
        return _events.value.takeLast(limit)
    }
    
    fun isStuck(): Boolean = stuckCounter >= 10
    fun getStuckDuration(): Int = stuckCounter
    
    fun clearEvents() {
        _events.value = emptyList()
        stuckCounter = 0
    }
}
