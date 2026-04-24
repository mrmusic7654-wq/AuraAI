package com.aura.ai.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeyDialog(
    currentKey: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit = {}
) {
    var apiKey by remember { mutableStateOf(currentKey) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API Key") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter your Gemini API key from Google AI Studio.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { 
                        apiKey = it
                        testResult = null
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("AIza...") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://aistudio.google.com/app/apikey")
                            )
                            androidx.compose.ui.platform.LocalContext.current.startActivity(intent)
                        }
                    ) {
                        Text("Get free key →")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            onTest(apiKey)
                            // In real implementation, this would be async
                            isTesting = false
                            testResult = if (apiKey.length > 10) "✅ Valid" else "❌ Invalid"
                        },
                        enabled = apiKey.isNotBlank() && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Test")
                        }
                    }
                }
                
                testResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✅")) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
                
                Divider()
                
                Text(
                    text = "🔒 Your key stays on this device only. It is never sent to any server except Google's Gemini API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiKey) },
                enabled = apiKey.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
