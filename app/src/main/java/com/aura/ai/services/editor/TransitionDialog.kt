package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TransitionDialog(
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTransition by remember { mutableStateOf("cut") }
    
    val transitions = mapOf(
        "cut" to "✂️ Cut (Instant)",
        "fade" to "🌅 Fade (Smooth)",
        "slide_left" to "👈 Slide Left",
        "slide_right" to "👉 Slide Right",
        "zoom_in" to "🔍 Zoom In",
        "zoom_out" to "🔎 Zoom Out",
        "wipe" to "🧹 Wipe"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("🎬 Add Transition", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                transitions.forEach { (key, label) ->
                    Surface(
                        onClick = { selectedTransition = key },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = if (selectedTransition == key) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTransition == key,
                                onClick = { selectedTransition = key },
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
                onClick = { onApply(selectedTransition) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
