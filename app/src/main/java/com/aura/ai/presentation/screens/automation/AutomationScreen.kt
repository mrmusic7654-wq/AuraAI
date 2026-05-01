package com.aura.ai.presentation.screens.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.components.AuraTopBar
import com.aura.ai.presentation.components.AutomationRuleCard

@Composable
fun AutomationScreen(viewModel: AutomationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(title = "Automation Rules")
        if (state.rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No automation rules yet")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.rules) { rule ->
                    AutomationRuleCard(
                        name = rule.name,
                        description = rule.description,
                        actionCount = rule.actionCount,
                        isEnabled = rule.isEnabled,
                        onToggle = { viewModel.toggleRuleEnabled(rule.id, it) },
                        onDelete = { viewModel.deleteRule(rule.id) }
                    )
                }
            }
        }
    }
}
