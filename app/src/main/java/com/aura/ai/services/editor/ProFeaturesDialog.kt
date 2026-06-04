package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
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
fun ProFeaturesDialog(
    onApply: (String, Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFeature by remember { mutableStateOf("color") }
    var paramValue by remember { mutableStateOf("") }
    
    val features = mapOf(
        "color" to "🎨 Color Grade",
        "speed" to "⚡ Speed Control",
        "volume" to "🔊 Volume Level",
        "crop" to "✂️ Crop/Resize",
        "filter" to "🌈 Filter",
        "pip" to "🖼️ Picture-in-Picture",
        "textanim" to "📝 Text Animation",
        "subtitle" to "💬 Auto-Subtitles",
        "denoise" to "🔇 Denoise",
        "stabilize" to "📹 Stabilize"
    )
    
    val params = when (selectedFeature) {
        "color" -> listOf("warm" to "Warm", "cool" to "Cool", "cinematic" to "Cinematic", "vintage" to "Vintage")
        "speed" -> listOf("0.25" to "0.25x", "0.5" to "0.5x", "1" to "1x", "2" to "2x", "4" to "4x")
        "volume" -> listOf("25" to "25%", "50" to "50%", "75" to "75%", "100" to "100%")
        "crop" -> listOf("16:9" to "16:9", "1:1" to "1:1", "9:16" to "9:16", "4:3" to "4:3")
        "filter" -> listOf("none" to "None", "vintage" to "Vintage", "bw" to "B&W", "warm" to "Warm", "cool" to "Cool", "dramatic" to "Dramatic")
        "pip" -> listOf("top-right" to "Top Right", "top-left" to "Top Left", "bottom-right" to "Bottom Right")
        "textanim" -> listOf("fade" to "Fade In", "slide" to "Slide Up", "typewriter" to "Typewriter", "bounce" to "Bounce")
        "subtitle" -> listOf("auto" to "Auto-Generate", "english" to "English", "hindi" to "Hindi")
        "denoise" -> listOf("light" to "Light", "medium" to "Medium", "heavy" to "Heavy")
        "stabilize" -> listOf("light" to "Light", "medium" to "Medium", "heavy" to "Heavy")
        else -> emptyList()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("🎬 Pro Features", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Feature selector
                Column(modifier = Modifier.height(120.dp).verticalScroll(rememberScrollState())) {
                    features.forEach { (key, label) ->
                        Surface(
                            onClick = { selectedFeature = key },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedFeature == key) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF333333),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Parameter selector
                Text("Parameter", color = Color.White, fontWeight = FontWeight.Bold)
                params.take(4).forEach { (value, label) ->
                    Surface(
                        onClick = { paramValue = value },
                        shape = RoundedCornerShape(8.dp),
                        color = if (paramValue == value) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            RadioButton(selected = paramValue == value, onClick = { paramValue = value })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(selectedFeature, mapOf("value" to paramValue)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
