package com.aura.ai.domain.repository

import com.aura.ai.data.models.ScreenContext
import kotlinx.coroutines.flow.StateFlow

interface ScreenStateRepository {
    
    val currentScreen: StateFlow<ScreenContext?>
    
    fun updateScreen(context: ScreenContext)
    
    fun getCurrentScreen(): ScreenContext?
    
    suspend fun waitForScreenChange(
        timeoutMs: Long = 5000,
        predicate: (ScreenContext) -> Boolean
    ): ScreenContext?
}
