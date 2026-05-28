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

data class Mask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "rectangle", "circle", "linear_gradient", "radial_gradient", "text", "freehand"
    val x: Float = 50f,    // center position (0-100%)
    val y: Float = 50f,
    val width: Float = 30f,  // size (0-100%)
    val height: Float = 30f,
    val rotation: Float = 0f,
    val feather: Float = 0f,  // edge softness (0-100)
    val inverted: Boolean = false
)

@Composable
fun MaskingTool(
    masks: List<Mask>,
    onAddMask: (Mask) -> Unit,
    onUpdateMask: (Mask) -> Unit,
    onDeleteMask: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val maskTypes = mapOf(
        "rectangle" to "▬ Rectangle",
        "circle" to "⭕ Circle",
        "linear_gradient" to "↕️ Linear Gradient",
        "radial_gradient" to "◎ Radial Gradient",
        "text" to "📝 Text Shape"
    )
    
    var selectedType by remember { mutableStateOf("rectangle") }
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(16.dp)
    ) {
        Text("🎭 MASKING TOOL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Existing masks
        Text("MASKS (${masks.size})", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        
        masks.forEach { mask ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(maskTypes[mask.type] ?: mask.type, color = Color.White, fontSize = 11.sp)
                        Text("Pos: (${mask.x}%, ${mask.y}%) | Size: ${mask.width}x${mask.height} | Feather: ${mask.feather}%", color = Color.Gray, fontSize = 9.sp)
                    }
                    IconButton(onClick = { onDeleteMask(mask.id) }, modifier = Modifier.size(24.dp)) {
                        Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Add mask
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ ADD MASK", fontSize = 12.sp) }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) { Text("Done", fontSize = 12.sp) }
    }
    
    if (showAddDialog) {
        AddMaskDialog(
            onAdd = { mask -> onAddMask(mask); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun AddMaskDialog(onAdd: (Mask) -> Unit, onDismiss: () -> Unit) {
    var selectedType by remember { mutableStateOf("rectangle") }
    var width by remember { mutableStateOf("30") }
    var height by remember { mutableStateOf("30") }
    var feather by remember { mutableStateOf("5") }
    var inverted by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Add Mask", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Type
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("rectangle", "circle", "linear_gradient", "radial_gradient").forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.replace("_", " "), color = Color.White, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = width, onValueChange = { width = it }, label = { Text("Width %", color = Color.Gray, fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height %", color = Color.Gray, fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = feather, onValueChange = { feather = it }, label = { Text("Feather %", color = Color.Gray, fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inverted", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = inverted, onCheckedChange = { inverted = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(Mask(type = selectedType, width = width.toFloatOrNull() ?: 30f, height = height.toFloatOrNull() ?: 30f, feather = feather.toFloatOrNull() ?: 5f, inverted = inverted)) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } }
    )
}
