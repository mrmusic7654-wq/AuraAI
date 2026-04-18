package com.aura.ai.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import com.aura.ai.domain.usecases.settings.SaveApiKeyUseCase
import com.aura.ai.domain.usecases.settings.UpdateUserPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveApiKeyUseCase: SaveApiKeyUseCase,
    private val updateUserPreferencesUseCase: UpdateUserPreferencesUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    
    fun setApiKey(apiKey: String) {
        saveApiKeyUseCase(apiKey)
        _state.update { it.copy(hasApiKey = true) }
    }
    
    fun setUserName(name: String) {
        _state.update { it.copy(userName = name) }
    }
    
    fun nextStep() {
        val currentStep = _state.value.currentStep
        _state.update { 
            it.copy(currentStep = OnboardingStep.values()[currentStep.ordinal + 1])
        }
    }
    
    fun completeOnboarding() {
        _state.update { 
            it.copy(
                isCompleted = true,
                currentStep = OnboardingStep.COMPLETED
            )
        }
        
        _state.value.userName?.let { name ->
            updateUserPreferencesUseCase.updateUserName(name)
        }
    }
    
    fun skipOnboarding() {
        _state.update { it.copy(isCompleted = true) }
    }
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val hasApiKey: Boolean = false,
    val userName: String? = null,
    val hasAccessibilityPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isCompleted: Boolean = false
)

enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    API_SETUP,
    GITHUB_SETUP,
    COMPLETED
}
