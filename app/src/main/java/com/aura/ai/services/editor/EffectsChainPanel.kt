package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class VideoEffect(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val category: String, // "blur", "distort", "stylize", "transition", "color"
    val params: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true
)

@Composable
fun EffectsChainPanel(
    effects: List<VideoEffect>,
    onAddEffect: (VideoEffect) -> Unit,
    onRemoveEffect: (String) -> Unit,
    onToggleEffect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableEffects = listOf(
        VideoEffect(name = "Gaussian Blur", category = "blur", params = mapOf("radius" to 10f)),
        VideoEffect(name = "Motion Blur", category = "blur", params = mapOf("angle" to 0f, "distance" to 20f)),
        VideoEffect(name = "Glitch", category = "distort", params = mapOf("intensity" to 50f)),
        VideoEffect(name = "Chromatic Aberration", category = "distort", params = mapOf("shift" to 5f)),
        VideoEffect(name = "Pixelate", category = "stylize", params = mapOf("size" to 10f)),
        VideoEffect(name = "Cartoon", category = "stylize", params = mapOf("threshold" to 30f)),
        VideoEffect(name = "Glow", category = "stylize", params = mapOf("intensity" to 40f, "radius" to 20f)),
        VideoEffect(name = "Shadow", category = "stylize", params = mapOf("opacity" to 50f, "offset" to 10f)),
        VideoEffect(name = "Mirror", category = "distort", params = mapOf("axis" to 0f)),
        VideoEffect(name = "Kaleidoscope", category = "stylize", params = mapOf("segments" to 6f)),
        VideoEffect(name = "VHS", category = "stylize", params = mapOf("intensity" to 30f)),
        VideoEffect(name = "Light Leak", category = "color", params = mapOf("intensity" to 40f)),
        VideoEffect(name = "Colorize", category = "color", params = mapOf("hue" to 180f, "intensity" to 50f)),
        VideoEffect(name = "Posterize", category = "color", params = mapOf("levels" to 4f))
    )
    
    var selectedCategory by remember { mutableStateOf("all") }
    val categories = listOf("all", "blur", "distort", "stylize", "color")
    
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(16.dp)
    ) {
        Text("✨ EFFECTS CHAIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Active effects
        Text("ACTIVE EFFECTS (${effects.size})", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(120.dp)) {
            itemsIndexed(effects) { index, effect ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = effect.enabled,
                            onCheckedChange = { onToggleEffect(effect.id) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
                        )
                        Text(" ${index + 1}. ${effect.name}", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveEffect(effect.id) }, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Category filter
        Text("ADD EFFECTS", color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.uppercase(), color = Color.White, fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Available effects
        val filteredEffects = if (selectedCategory == "all") availableEffects 
            else availableEffects.filter { it.category == selectedCategory }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(150.dp)) {
            itemsIndexed(filteredEffects) { _, effect ->
                Surface(
                    onClick = { onAddEffect(effect) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF333333),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text("➕", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(effect.name, color = Color.White, fontSize = 11.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(effect.category.uppercase(), color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) { Text("Done", fontSize = 12.sp) }
    }
}
