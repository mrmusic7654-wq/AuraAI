package com.aura.ai.domain.usecases.accessibility

import com.aura.ai.data.models.ScreenContext
import com.aura.ai.services.AuraAccessibilityService
import javax.inject.Inject

class CaptureScreenStateUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    operator fun invoke(): ScreenContext? {
        return accessibilityService?.captureCurrentScreen()
    }
}
