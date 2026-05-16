package com.aura.ai.presentation.screens.automation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    fun showCreateDialog() { _state.value = _state.value.copy(showCreateDialog = true) }
    fun hideCreateDialog() { _state.value = _state.value.copy(showCreateDialog = false) }
    fun toggleRuleEnabled(ruleId: String, enabled: Boolean) {}
    fun deleteRule(ruleId: String) {}
    fun createRule(name: String, description: String?, triggerApp: String?, triggerText: String?) {
        hideCreateDialog()
    }
}
