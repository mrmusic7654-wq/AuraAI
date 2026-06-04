package com.aura.ai.services.orchestrator

import com.aura.ai.services.AppController
import com.aura.ai.services.AuraAccessibilityService
import kotlinx.coroutines.delay

class ActionExecutor(private val appController: AppController) {
    
    suspend fun executeAction(action: String, params: Map<String, String>): String {
        return when (action) {
            "open_app" -> {
                val pkg = params["package"] ?: return "Missing package name"
                appController.openApp(pkg)
                "Opened: $pkg"
            }
            "tap" -> {
                val text = params["text"] ?: return "Missing tap target"
                appController.read()
                "Tapped: $text"
            }
            "type" -> {
                val text = params["text"] ?: return "Missing text to type"
                appController.read()
                "Typed: ${text.take(50)}"
            }
            "swipe" -> {
                val direction = params["direction"] ?: "down"
                appController.read()
                "Swiped: $direction"
            }
            "read_screen" -> {
                appController.read()
            }
            "wait_for" -> {
                val timeout = params["timeout"]?.toLongOrNull() ?: 5000L
                delay(timeout)
                "Waited: ${timeout}ms"
            }
            "press_back" -> {
                AuraAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
                "Back pressed"
            }
            "press_home" -> {
                AuraAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
                "Home pressed"
            }
            "press_recents" -> {
                AuraAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                )
                "Recents opened"
            }
            "take_screenshot" -> {
                AuraAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                )
                "Screenshot taken"
            }
            "open_notifications" -> {
                AuraAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )
                "Notifications opened"
            }
            else -> "Unknown action: $action"
        }
    }
    
    suspend fun executeMultiple(actions: List<Pair<String, Map<String, String>>>): List<String> {
        val results = mutableListOf<String>()
        for ((action, params) in actions) {
            results.add(executeAction(action, params))
            delay(500) // Small delay between actions
        }
        return results
    }
    
    suspend fun executeSequence(steps: List<ActionStep>): List<String> {
        val results = mutableListOf<String>()
        for (step in steps) {
            try {
                val result = withTimeoutOrNull(step.timeoutMs) {
                    executeAction(step.action, step.params)
                } ?: "Timeout: ${step.action}"
                results.add(result)
                
                if (step.waitAfterMs > 0) {
                    delay(step.waitAfterMs)
                }
            } catch (e: Exception) {
                results.add("Failed: ${step.action} - ${e.message}")
                if (!step.continueOnFailure) break
            }
        }
        return results
    }
    
    data class ActionStep(
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val timeoutMs: Long = 30000,
        val waitAfterMs: Long = 1000,
        val continueOnFailure: Boolean = true
    )
    
    suspend fun withTimeoutOrNull(timeoutMs: Long, block: suspend () -> String): String? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                block()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
    }
}
