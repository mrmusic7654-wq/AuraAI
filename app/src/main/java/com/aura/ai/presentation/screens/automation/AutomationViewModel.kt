package com.aura.ai.presentation.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.domain.usecases.automation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val getRulesUseCase: GetRulesUseCase,
    private val createRuleUseCase: CreateRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val evaluateRuleUseCase: EvaluateRuleUseCase,
    private val executeRuleUseCase: ExecuteRuleUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()
    
    init {
        loadRules()
    }
    
    private fun loadRules() {
        viewModelScope.launch {
            getRulesUseCase().collect { rules ->
                _state.update { it.copy(rules = rules) }
            }
        }
    }
    
    fun createRule(
        name: String,
        description: String?,
        triggerApp: String?,
        triggerText: String?
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            
            val result = createRuleUseCase(
                name = name,
                description = description,
                triggerApp = triggerApp,
                triggerText = triggerText,
                triggerTime = null,
                actions = emptyList() // Actions would be configured separately
            )
            
            result.fold(
                onSuccess = { ruleId ->
                    _state.update { 
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isCreating = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }
    
    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            deleteRuleUseCase(ruleId)
        }
    }
    
    fun toggleRuleEnabled(ruleId: String, enabled: Boolean) {
        // Implementation would need an UpdateRuleUseCase
    }
    
    fun showCreateDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }
    
    fun hideCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
