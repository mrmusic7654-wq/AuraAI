package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TimelineTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "video", "audio", "text", "effect"
    val clips: List<TimelineClip> = emptyList(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true
)

data class TimelineClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startTime: Float, // seconds
    val duration: Float,  // seconds
    val color: Color = Color(0xFF4CAF50)
)

@Composable
fun TimelineMultiTrack(
    tracks: List<TimelineTrack>,
    currentTime: Float,
    onSelectClip: (TimelineClip) -> Unit,
    onMoveClip: (TimelineClip, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val trackColors = mapOf(
        "video" to Color(0xFF4CAF50),
        "audio" to Color(0xFF2196F3),
        "text" to Color(0xFFFFC107),
        "effect" to Color(0xFF9C27B0)
    )
    
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(16.dp)
    ) {
        Text("📐 MULTI-TRACK TIMELINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        
        // Time ruler
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("⏱️ ${String.format("%.1f", currentTime)}s", color = Color(0xFF4CAF50), fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("Total: ${String.format("%.1f", tracks.maxOfOrNull { track -> track.clips.maxOfOrNull { it.startTime + it.duration } ?: 0f } ?: 0f)}s", color = Color.Gray, fontSize = 10.sp)
        }
        
        // Tracks
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(300.dp)) {
            itemsIndexed(tracks) { index, track ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Track header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null,
                                tint = if (track.isVisible) Color.White else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(track.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(color = trackColors[track.type] ?: Color.Gray, shape = RoundedCornerShape(4.dp)) {
                                Text(track.type.uppercase(), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 8.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (track.isLocked) Text("🔒", fontSize = 10.sp)
                        }
                        
                        // Clips row
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(track.clips) { clipIndex, clip ->
                                Surface(
                                    onClick = { onSelectClip(clip) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = clip.color.copy(alpha = 0.3f),
                                    modifier = Modifier.width((clip.duration * 20).dp).height(40.dp)
                                ) {
                                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.Center) {
                                        Text(clip.name, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${String.format("%.1f", clip.duration)}s", color = Color.Gray, fontSize = 7.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Controls
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            ControlButton("⏮️", "Start")
            ControlButton("⏪", "Back")
            ControlButton("▶️", "Play")
            ControlButton("⏩", "Forward")
            ControlButton("⏭️", "End")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.fillMaxWidth()) { Text("Close Timeline", fontSize = 12.sp) }
    }
}

@Composable
private fun ControlButton(icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { }.padding(4.dp)) {
        Text(icon, fontSize = 18.sp)
        Text(label, color = Color.Gray, fontSize = 8.sp)
    }
}
