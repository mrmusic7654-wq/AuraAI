package com.aura.ai.services

import com.aura.ai.data.models.ScreenContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateManager @Inject constructor() {
    private val _currentScreen = MutableStateFlow<ScreenContext?>(null)
    val currentScreen: StateFlow<ScreenContext?> = _currentScreen.asStateFlow()
    fun updateScreen(context: ScreenContext) { _currentScreen.value = context }
}
