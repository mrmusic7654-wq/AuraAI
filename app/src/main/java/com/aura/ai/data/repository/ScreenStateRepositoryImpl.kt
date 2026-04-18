package com.aura.ai.data.repository

import com.aura.ai.data.models.ScreenContext
import com.aura.ai.domain.repository.ScreenStateRepository
import com.aura.ai.services.ScreenStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateRepositoryImpl @Inject constructor(
    private val screenStateManager: ScreenStateManager
) : ScreenStateRepository {
    
    override val currentScreen: StateFlow<ScreenContext?>
        get() = screenStateManager.currentScreen
    
    override fun updateScreen(context: ScreenContext) {
        screenStateManager.updateScreen(context)
    }
    
    override fun getCurrentScreen(): ScreenContext? {
        return screenStateManager.currentScreen.value
    }
    
    override suspend fun waitForScreenChange(
        timeoutMs: Long,
        predicate: (ScreenContext) -> Boolean
    ): ScreenContext? {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val current = getCurrentScreen()
            if (current != null && predicate(current)) {
                return current
            }
            delay(100)
        }
        
        return null
    }
}
