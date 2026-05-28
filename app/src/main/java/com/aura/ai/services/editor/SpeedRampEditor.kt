package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SpeedRamp(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timeStart: Float,  // seconds
    val timeEnd: Float,
    val speedStart: Float, // 0.1x to 10x
    val speedEnd: Float,
    val curve: String = "linear" // "linear", "ease_in", "ease_out", "s_curve"
)

@Composable
fun SpeedRampEditor(
    ramps: List<SpeedRamp>,
    onAddRamp: (SpeedRamp) -> Unit,
    onDeleteRamp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var timeStart by remember { mutableStateOf("") }
    var timeEnd by remember { mutableStateOf("") }
    var speedStart by remember { mutableStateOf("1") }
    var speedEnd by remember { mutableStateOf("1") }
    var selectedCurve by remember { mutableStateOf("linear") }
    
    val presets = mapOf(
        "Slow Motion" to Pair(1f, 0.25f),
        "Speed Up" to Pair(1f, 3f),
        "Slow Down" to Pair(3f, 1f),
        "Fast Forward" to Pair(1f, 5f),
        "Reverse Slow" to Pair(0.5f, 0.5f),
        "Boomerang" to Pair(1f, -1f)
    )
    
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(16.dp)
    ) {
        Text("⚡ SPEED RAMP EDITOR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Presets
        Text("Presets", color = Color.Gray, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            presets.keys.take(4).forEach { preset ->
                val (start, end) = presets[preset]!!
                FilterChip(
                    selected = false,
                    onClick = {
                        speedStart = start.toString()
                        speedEnd = end.toString()
                    },
                    label = { Text("$preset\n${start}x→${end}x", color = Color.White, fontSize = 8.sp, lineHeight = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Custom ramp
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("CUSTOM RAMP", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = timeStart, onValueChange = { timeStart = it }, label = { Text("Start Time (s)", color = Color.Gray, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = timeEnd, onValueChange = { timeEnd = it }, label = { Text("End Time (s)", color = Color.Gray, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = speedStart, onValueChange = { speedStart = it }, label = { Text("Start Speed (x)", color = Color.Gray, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = speedEnd, onValueChange = { speedEnd = it }, label = { Text("End Speed (x)", color = Color.Gray, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("Curve", color = Color.Gray, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("linear", "ease_in", "ease_out", "s_curve").forEach { curve ->
                        FilterChip(
                            selected = selectedCurve == curve,
                            onClick = { selectedCurve = curve },
                            label = { Text(curve.replace("_", " "), color = Color.White, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onAddRamp(SpeedRamp(
                            timeStart = timeStart.toFloatOrNull() ?: 0f,
                            timeEnd = timeEnd.toFloatOrNull() ?: 0f,
                            speedStart = speedStart.toFloatOrNull() ?: 1f,
                            speedEnd = speedEnd.toFloatOrNull() ?: 1f,
                            curve = selectedCurve
                        ))
                        timeStart = ""; timeEnd = ""; speedStart = "1"; speedEnd = "1"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Ramp", fontSize = 12.sp) }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Existing ramps
        Text("RAMPS (${ramps.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        ramps.forEach { ramp ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ramp.timeStart}s - ${ramp.timeEnd}s", color = Color.White, fontSize = 11.sp)
                        Text("${ramp.speedStart}x → ${ramp.speedEnd}x | ${ramp.curve}", color = Color.Gray, fontSize = 9.sp)
                    }
                    IconButton(onClick = { onDeleteRamp(ramp.id) }, modifier = Modifier.size(24.dp)) {
                        Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) { Text("Done", fontSize = 12.sp) }
    }
}
