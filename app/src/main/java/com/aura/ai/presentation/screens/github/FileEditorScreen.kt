package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun FileEditorScreen(
    filePath: String,
    initialContent: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var commitMessage by remember { mutableStateOf("Update $filePath") }
    var showCommitDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = filePath,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showCommitDialog = true }) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        )
        
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
    
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit Changes") },
            text = {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit message") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(content, commitMessage)
                        showCommitDialog = false
                    }
                ) {
                    Text("Commit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
