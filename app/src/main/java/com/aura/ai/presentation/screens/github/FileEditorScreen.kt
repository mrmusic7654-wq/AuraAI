package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
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
    onSave: (String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(title = filePath)
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxSize().padding(16.dp))
        Button(onClick = { onSave(content) }, modifier = Modifier.padding(16.dp)) { Text("Save") }
    }
}
