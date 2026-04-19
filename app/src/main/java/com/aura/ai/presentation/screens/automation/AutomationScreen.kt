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
fun AutomationScreen(
    viewModel: AutomationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AuraTopBar(
            title = "Automation Rules",
            actions = {
                IconButton(onClick = { viewModel.showCreateDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        )
        
        if (state.rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No automation rules yet.\nTap + to create one.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.rules) { rule ->
                    AutomationRuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            viewModel.toggleRuleEnabled(rule.id, enabled)
                        },
                        onDelete = {
                            viewModel.deleteRule(rule.id)
                        }
                    )
                }
            }
        }
    }
    
    // Create Rule Dialog
    if (state.showCreateDialog) {
        CreateRuleDialog(
            onDismiss = { viewModel.hideCreateDialog() },
            onConfirm = { name, description, triggerApp, triggerText ->
                viewModel.createRule(name, description, triggerApp, triggerText)
            }
        )
    }
}

@Composable
fun CreateRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var triggerApp by remember { mutableStateOf("") }
    var triggerText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Automation Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description ?: "",
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = triggerApp ?: "",
                    onValueChange = { triggerApp = it },
                    label = { Text("Trigger App Package (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = triggerText ?: "",
                    onValueChange = { triggerText = it },
                    label = { Text("Trigger Text (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, triggerApp, triggerText) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
