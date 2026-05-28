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

data class Subtitle(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTime: Float,
    val endTime: Float,
    val text: String,
    val style: String = "normal" // "normal", "bold", "italic"
)

@Composable
fun SubtitleGenerator(
    subtitles: List<Subtitle>,
    onGenerate: (String) -> Unit,
    onEditSubtitle: (Subtitle) -> Unit,
    onDeleteSubtitle: (String) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    var language by remember { mutableStateOf("english") }
    var isGenerating by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(16.dp)
    ) {
        Text("💬 SUBTITLE GENERATOR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Language selection
        Text("Language", color = Color.Gray, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("english", "hindi", "spanish", "french", "german", "japanese", "korean", "auto").forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = { language = lang },
                    label = { Text(lang.uppercase(), color = Color.White, fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Generate button
        Button(
            onClick = {
                isGenerating = true
                onGenerate(language)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating... (using Gemini)", fontSize = 12.sp)
            } else {
                Text("🤖 AUTO-GENERATE SUBTITLES", fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Subtitles list
        Text("SUBTITLES (${subtitles.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(250.dp)) {
            itemsIndexed(subtitles) { index, sub ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${index + 1}. ${sub.text}", color = Color.White, fontSize = 11.sp, maxLines = 2)
                            Text("${String.format("%.1f", sub.startTime)}s - ${String.format("%.1f", sub.endTime)}s | ${sub.style}", color = Color.Gray, fontSize = 9.sp)
                        }
                        IconButton(onClick = { onDeleteSubtitle(sub.id) }, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.weight(1f)) { Text("Export .SRT", fontSize = 12.sp) }
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.weight(1f)) { Text("Done", fontSize = 12.sp) }
        }
    }
}
