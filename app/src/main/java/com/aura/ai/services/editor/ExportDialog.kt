package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ExportDialog(
    onExport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var filename by remember { mutableStateOf("my_video") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    
    val qualities = listOf("720p", "1080p", "4K")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("📤 Export Video", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("Filename", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4CAF50), unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Quality", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualities.forEach { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality },
                            label = { Text(quality, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            )
                        )
                    }
                }
                
                Text("⏱️ Estimated time: ${when(selectedQuality) { "4K" -> "5-10 min"; "1080p" -> "2-5 min"; else -> "1-3 min" }}", 
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedQuality) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Start Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
