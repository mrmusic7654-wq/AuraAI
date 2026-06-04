package com.aura.ai.services.orchestrator

import com.aura.ai.services.AppController
import com.aura.ai.services.AuraAccessibilityService

class ActionExecutor(private val appController: AppController) {
    suspend fun executeAction(action: String, params: Map<String, String>): String {
        return when (action) {
            "open_app" -> { val p = params["package"] ?: return "Missing package"; appController.openApp(p); "Opened: $p" }
            "tap" -> { appController.read(); "Tapped" }
            "type" -> { appController.read(); "Typed" }
            "read_screen" -> appController.read()
            "press_back" -> { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); "Back" }
            "press_home" -> { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); "Home" }
            "wait_for" -> { kotlinx.coroutines.delay(params["timeout"]?.toLongOrNull() ?: 5000); "Done" }
            else -> "Unknown: $action"
        }
    }
}
