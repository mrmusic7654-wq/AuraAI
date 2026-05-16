package com.aura.ai.domain.usecases.accessibility

import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import javax.inject.Inject

class PerformTypeUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    suspend operator fun invoke(text: String): Boolean {
        return accessibilityService?.submitAction(AccessibilityAction.Type(text)) ?: false
    }
}
