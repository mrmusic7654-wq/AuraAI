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

data class ColorGrade(
    val exposure: Float = 0f,      // -5 to +5
    val contrast: Float = 1f,      // 0 to 2
    val saturation: Float = 1f,    // 0 to 2
    val temperature: Float = 0f,   // -100 to +100 (Kelvin shift)
    val tint: Float = 0f,          // -100 to +100 (green-magenta)
    val shadows: Float = 0f,       // -100 to +100
    val midtones: Float = 0f,      // -100 to +100
    val highlights: Float = 0f,    // -100 to +100
    val sharpness: Float = 0f,     // 0 to 100
    val vignette: Float = 0f,      // 0 to 100
    val grain: Float = 0f          // 0 to 100
)

@Composable
fun ColorGradingPanel(
    currentGrade: ColorGrade,
    onApply: (ColorGrade) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var grade by remember { mutableStateOf(currentGrade) }
    
    val presets = mapOf(
        "none" to ColorGrade(),
        "cinematic" to ColorGrade(contrast = 1.3f, saturation = 1.2f, temperature = 10f, vignette = 30f),
        "warm" to ColorGrade(temperature = 50f, saturation = 1.1f, vignette = 20f),
        "cool" to ColorGrade(temperature = -50f, contrast = 1.1f),
        "vintage" to ColorGrade(saturation = 0.7f, contrast = 1.2f, grain = 20f, vignette = 40f),
        "dramatic" to ColorGrade(contrast = 1.6f, saturation = 1.3f, shadows = -20f, highlights = 20f),
        "flat" to ColorGrade(contrast = 0.8f, saturation = 0.9f, sharpness = 10f),
        "noir" to ColorGrade(saturation = 0f, contrast = 1.5f, grain = 30f, vignette = 50f)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        Text("🎨 COLOR GRADING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Presets
        Text("Presets", color = Color.Gray, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            presets.keys.take(5).forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { grade = presets[preset]!! },
                    label = { Text(preset.uppercase(), color = Color.White, fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Numeric controls
        val controls = listOf(
            "exposure" to "☀️ Exposure" to Pair(-5f, 5f),
            "contrast" to "🌓 Contrast" to Pair(0f, 2f),
            "saturation" to "🌈 Saturation" to Pair(0f, 2f),
            "temperature" to "🔥 Temperature" to Pair(-100f, 100f),
            "shadows" to "🌑 Shadows" to Pair(-100f, 100f),
            "highlights" to "🌕 Highlights" to Pair(-100f, 100f),
            "sharpness" to "🔪 Sharpness" to Pair(0f, 100f),
            "vignette" to "🔳 Vignette" to Pair(0f, 100f),
            "grain" to "📺 Grain" to Pair(0f, 100f)
        )
        
        controls.take(8).forEach { (pair, range) ->
            val (key, label) = pair
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(100.dp))
                Slider(
                    value = when (key) {
                        "exposure" -> grade.exposure; "contrast" -> grade.contrast
                        "saturation" -> grade.saturation; "temperature" -> grade.temperature
                        "shadows" -> grade.shadows; "highlights" -> grade.highlights
                        "sharpness" -> grade.sharpness; "vignette" -> grade.vignette
                        "grain" -> grade.grain; else -> 0f
                    },
                    onValueChange = { newValue ->
                        grade = when (key) {
                            "exposure" -> grade.copy(exposure = newValue)
                            "contrast" -> grade.copy(contrast = newValue)
                            "saturation" -> grade.copy(saturation = newValue)
                            "temperature" -> grade.copy(temperature = newValue)
                            "shadows" -> grade.copy(shadows = newValue)
                            "highlights" -> grade.copy(highlights = newValue)
                            "sharpness" -> grade.copy(sharpness = newValue)
                            "vignette" -> grade.copy(vignette = newValue)
                            "grain" -> grade.copy(grain = newValue)
                            else -> grade
                        }
                    },
                    valueRange = range.first..range.second,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                )
                Text(
                    String.format("%.1f", when (key) {
                        "exposure" -> grade.exposure; "contrast" -> grade.contrast
                        "saturation" -> grade.saturation; "temperature" -> grade.temperature
                        "shadows" -> grade.shadows; "highlights" -> grade.highlights
                        "sharpness" -> grade.sharpness; "vignette" -> grade.vignette
                        "grain" -> grade.grain; else -> 0f
                    }),
                    color = Color.White, fontSize = 10.sp, modifier = Modifier.width(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), modifier = Modifier.weight(1f)) { Text("Reset", fontSize = 12.sp) }
            Button(onClick = { onApply(grade) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.weight(1f)) { Text("Apply Grade", fontSize = 12.sp) }
        }
    }
}
