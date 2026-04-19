package com.aura.ai.presentation.screens.automation

import com.aura.ai.domain.usecases.automation.AutomationRuleWithActions

data class AutomationState(
    val rules: List<AutomationRuleWithActions> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val showCreateDialog: Boolean = false,
    val selectedRule: AutomationRuleWithActions? = null,
    val error: String? = null
)
