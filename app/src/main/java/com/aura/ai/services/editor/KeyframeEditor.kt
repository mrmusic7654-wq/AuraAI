package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Keyframe(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timeSeconds: Float,
    val property: String, // "opacity", "scale", "rotation", "position_x", "position_y", "volume"
    val value: Float,
    val easing: String = "linear" // "linear", "ease_in", "ease_out", "ease_in_out", "bounce"
)

@Composable
fun KeyframeEditor(
    keyframes: List<Keyframe>,
    onAddKeyframe: (Keyframe) -> Unit,
    onDeleteKeyframe: (String) -> Unit,
    onUpdateKeyframe: (Keyframe) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProperty by remember { mutableStateOf("opacity") }
    var timeValue by remember { mutableStateOf("") }
    var propertyValue by remember { mutableStateOf("") }
    var selectedEasing by remember { mutableStateOf("linear") }
    
    val properties = mapOf(
        "opacity" to "👁️ Opacity (0-100)",
        "scale" to "🔍 Scale (0.1-5.0)",
        "rotation" to "🔄 Rotation (0-360)",
        "position_x" to "↔️ Position X (0-100)",
        "position_y" to "↕️ Position Y (0-100)",
        "volume" to "🔊 Volume (0-100)"
    )
    
    val easings = listOf("linear", "ease_in", "ease_out", "ease_in_out", "bounce", "elastic")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        Text("🎯 KEYFRAME EDITOR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Add Keyframe Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ADD KEYFRAME", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Property selector
                Text("Property", color = Color.Gray, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    properties.keys.take(4).forEach { key ->
                        FilterChip(
                            selected = selectedProperty == key,
                            onClick = { selectedProperty = key },
                            label = { Text(key.replace("_", " "), color = Color.White, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeValue,
                        onValueChange = { timeValue = it },
                        label = { Text("Time (s)", color = Color.Gray, fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = propertyValue,
                        onValueChange = { propertyValue = it },
                        label = { Text("Value", color = Color.Gray, fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Easing selector
                Text("Easing", color = Color.Gray, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    easings.take(5).forEach { easing ->
                        FilterChip(
                            selected = selectedEasing == easing,
                            onClick = { selectedEasing = easing },
                            label = { Text(easing.replace("_", " "), color = Color.White, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        val kf = Keyframe(
                            timeSeconds = timeValue.toFloatOrNull() ?: 0f,
                            property = selectedProperty,
                            value = propertyValue.toFloatOrNull() ?: 0f,
                            easing = selectedEasing
                        )
                        onAddKeyframe(kf)
                        timeValue = ""; propertyValue = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Keyframe", fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Keyframe List
        Text("KEYFRAMES (${keyframes.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(200.dp)
        ) {
            itemsIndexed(keyframes.sortedBy { it.timeSeconds }) { index, kf ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${kf.property}: ${kf.value}", color = Color.White, fontSize = 11.sp)
                            Text("@${kf.timeSeconds}s | ${kf.easing}", color = Color.Gray, fontSize = 9.sp)
                        }
                        IconButton(onClick = { onDeleteKeyframe(kf.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                modifier = Modifier.weight(1f)
            ) { Text("Close", fontSize = 12.sp) }
        }
    }
}
