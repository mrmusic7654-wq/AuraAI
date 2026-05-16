package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

class ExecuteActionUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    suspend operator fun invoke(action: AgentAction): Boolean {
        val service = accessibilityService
        if (service == null) {
            Timber.w("Accessibility service not available")
            return false
        }
        
        return try {
            when (action.type) {
                com.aura.ai.data.models.ActionType.TAP -> executeTap(service, action)
                com.aura.ai.data.models.ActionType.SWIPE -> executeSwipe(service, action)
                com.aura.ai.data.models.ActionType.TYPE -> executeType(service, action)
                com.aura.ai.data.models.ActionType.BACK -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                com.aura.ai.data.models.ActionType.HOME -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                com.aura.ai.data.models.ActionType.RECENTS -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                com.aura.ai.data.models.ActionType.OPEN_APP -> executeOpenApp(service, action)
                com.aura.ai.data.models.ActionType.WAIT -> {
                    delay(action.duration)
                    true
                }
                com.aura.ai.data.models.ActionType.FIND_AND_TAP -> executeFindAndTap(service, action)
                else -> false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute action: $action")
            false
        }
    }
    
    private suspend fun executeTap(service: AuraAccessibilityService, action: AgentAction): Boolean {
        return if (action.x != null && action.y != null) {
            service.submitAction(AccessibilityAction.Tap(action.x, action.y))
        } else if (action.target != null) {
            service.submitAction(AccessibilityAction.FindAndTap(action.target))
        } else {
            false
        }
    }
    
    private suspend fun executeSwipe(service: AuraAccessibilityService, action: AgentAction): Boolean {
        return if (action.startX != null && action.startY != null && 
                   action.endX != null && action.endY != null) {
            service.submitAction(
                AccessibilityAction.Swipe(
                    action.startX, action.startY,
                    action.endX, action.endY,
                    action.duration
                )
            )
        } else {
            false
        }
    }
    
    private suspend fun executeType(service: AuraAccessibilityService, action: AgentAction): Boolean {
        return if (action.text != null) {
            service.submitAction(AccessibilityAction.Type(action.text))
        } else {
            false
        }
    }
    
    private suspend fun executeOpenApp(service: AuraAccessibilityService, action: AgentAction): Boolean {
        return if (action.packageName != null) {
            service.submitAction(AccessibilityAction.OpenApp(action.packageName))
        } else {
            false
        }
    }
    
    private suspend fun executeFindAndTap(service: AuraAccessibilityService, action: AgentAction): Boolean {
        return if (action.target != null) {
            service.submitAction(AccessibilityAction.FindAndTap(action.target))
        } else {
            false
        }
    }
}
