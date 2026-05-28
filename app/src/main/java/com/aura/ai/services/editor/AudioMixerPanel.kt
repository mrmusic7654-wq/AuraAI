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

data class AudioTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val volume: Float = 1f,        // 0 to 1
    val pan: Float = 0f,           // -1 (left) to 1 (right)
    val muted: Boolean = false,
    val solo: Boolean = false,
    val effects: List<String> = emptyList()
)

@Composable
fun AudioMixerPanel(
    tracks: List<AudioTrack>,
    onUpdateTrack: (AudioTrack) -> Unit,
    onAddTrack: () -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var localTracks by remember { mutableStateOf(tracks) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        Text("🔊 AUDIO MIXER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Master controls
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("MASTER", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vol", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(30.dp))
                    Slider(value = 0.8f, onValueChange = {}, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC107), activeTrackColor = Color(0xFFFFC107)))
                    Text("80%", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(35.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Track list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(300.dp)) {
            itemsIndexed(localTracks) { index, track ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                            Text(track.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            
                            // Mute/Solo buttons
                            FilterChip(
                                selected = track.muted,
                                onClick = { onUpdateTrack(track.copy(muted = !track.muted)) },
                                label = { Text("M", color = if (track.muted) Color(0xFFEF4444) else Color.Gray, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.3f)),
                                modifier = Modifier.height(24.dp)
                            )
                            FilterChip(
                                selected = track.solo,
                                onClick = { onUpdateTrack(track.copy(solo = !track.solo)) },
                                label = { Text("S", color = if (track.solo) Color(0xFFFFC107) else Color.Gray, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFC107).copy(alpha = 0.3f)),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        
                        // Volume slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vol", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(25.dp))
                            Slider(
                                value = track.volume,
                                onValueChange = { onUpdateTrack(track.copy(volume = it)) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                            )
                            Text("${(track.volume * 100).toInt()}%", color = Color.White, fontSize = 9.sp, modifier = Modifier.width(35.dp))
                        }
                        
                        // Pan slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pan", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.width(25.dp))
                            Slider(
                                value = track.pan,
                                onValueChange = { onUpdateTrack(track.copy(pan = it)) },
                                valueRange = -1f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2196F3), activeTrackColor = Color(0xFF2196F3))
                            )
                            Text(if (track.pan < 0) "L${(-track.pan * 100).toInt()}" else if (track.pan > 0) "R${(track.pan * 100).toInt()}" else "C", 
                                color = Color.White, fontSize = 9.sp, modifier = Modifier.width(30.dp))
                        }
                        
                        // Effects
                        if (track.effects.isNotEmpty()) {
                            Text(track.effects.joinToString(" | "), color = Color(0xFF4CAF50), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddTrack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)), modifier = Modifier.weight(1f)) { Text("+ Add Track", fontSize = 11.sp) }
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.weight(1f)) { Text("Done", fontSize = 11.sp) }
        }
    }
}
