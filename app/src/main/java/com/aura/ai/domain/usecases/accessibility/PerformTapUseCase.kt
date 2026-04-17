package com.aura.ai.domain.usecases.accessibility

import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import javax.inject.Inject

class PerformTapUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    suspend operator fun invoke(x: Float, y: Float): Boolean {
        return accessibilityService?.submitAction(AccessibilityAction.Tap(x, y)) ?: false
    }
    
    suspend fun onElement(elementId: String): Boolean {
        return accessibilityService?.submitAction(
            AccessibilityAction.FindAndTap(elementId)
        ) ?: false
    }
}
