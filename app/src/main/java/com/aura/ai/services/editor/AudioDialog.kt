package com.aura.ai.presentation.screens.editor

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AudioDialog(
    onApply: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAudio by remember { mutableStateOf("none") }
    
    val audioOptions = mapOf(
        "none" to "🔇 No Audio",
        "bg_calm" to "🎵 Calm Background",
        "bg_upbeat" to "🎵 Upbeat Background",
        "bg_dramatic" to "🎵 Dramatic Background",
        "custom" to "📁 Custom Audio File"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("🎵 Add Audio", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                audioOptions.forEach { (key, label) ->
                    Surface(
                        onClick = { selectedAudio = key },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = if (selectedAudio == key) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            RadioButton(
                                selected = selectedAudio == key,
                                onClick = { selectedAudio = key },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(Uri.parse("file://audio/$selectedAudio")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Apply Audio") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
