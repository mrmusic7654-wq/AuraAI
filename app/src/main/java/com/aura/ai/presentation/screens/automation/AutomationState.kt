package com.aura.ai.presentation.screens.automation

data class AutomationState(
    val rules: List<AutomationRuleData> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val error: String? = null
)

data class AutomationRuleData(
    val id: String,
    val name: String,
    val description: String?,
    val actionCount: Int,
    val isEnabled: Boolean
)
