package com.aura.ai.services.intelligence

import com.aura.ai.services.AppController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitorEvent(val type: String, val message: String)

class ActiveMonitor(private val appController: AppController) {
    private val _events = MutableStateFlow<List<MonitorEvent>>(emptyList())
    val events: StateFlow<List<MonitorEvent>> = _events.asStateFlow()
    private var lastText = ""; private var stuckCount = 0
    
    suspend fun check(): List<MonitorEvent> {
        val current = appController.read()
        val newEvents = mutableListOf<MonitorEvent>()
        if (current == lastText && current.isNotEmpty()) { stuckCount++; if (stuckCount >= 10) newEvents.add(MonitorEvent("STUCK", "Screen unchanged")) }
        else stuckCount = 0
        if (current.contains("Allow") || current.contains("OK")) newEvents.add(MonitorEvent("POPUP", "Dialog detected"))
        lastText = current
        if (newEvents.isNotEmpty()) _events.value = _events.value + newEvents
        return newEvents
    }
}
