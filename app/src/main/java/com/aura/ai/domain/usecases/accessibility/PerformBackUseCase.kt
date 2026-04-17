package com.aura.ai.domain.usecases.accessibility

import android.accessibilityservice.AccessibilityService
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import javax.inject.Inject

class PerformBackUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    suspend operator fun invoke(): Boolean {
        return accessibilityService?.submitAction(
            AccessibilityAction.GlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        ) ?: false
    }
}
