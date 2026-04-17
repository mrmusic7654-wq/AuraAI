package com.aura.ai.domain.usecases.accessibility

import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import javax.inject.Inject

class PerformSwipeUseCase @Inject constructor() {
    
    private var accessibilityService: AuraAccessibilityService? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    suspend operator fun invoke(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 300
    ): Boolean {
        return accessibilityService?.submitAction(
            AccessibilityAction.Swipe(startX, startY, endX, endY, duration)
        ) ?: false
    }
    
    suspend fun swipeUp(): Boolean {
        return invoke(540f, 1500f, 540f, 500f, 300)
    }
    
    suspend fun swipeDown(): Boolean {
        return invoke(540f, 500f, 540f, 1500f, 300)
    }
    
    suspend fun swipeLeft(): Boolean {
        return invoke(900f, 1000f, 200f, 1000f, 300)
    }
    
    suspend fun swipeRight(): Boolean {
        return invoke(200f, 1000f, 900f, 1000f, 300)
    }
}
