package com.aura.ai.presentation.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(viewModel: VideoEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("🎬 Aura Video Editor", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A)),
            actions = {
                TextButton(onClick = { viewModel.exportVideo() }) {
                    Text("EXPORT", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        )
        
        // Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.Black)
                .border(1.dp, Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            if (state.isExporting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Exporting... ${state.exportProgress}%", color = Color.White)
                }
            } else {
                Text(
                    state.currentScene?.name ?: "No scene selected",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
        
        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousScene() }) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { viewModel.togglePlay() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = { viewModel.nextScene() }) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        
        // Timeline Info
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TimelineInfo("Scenes", "${state.scenes.size}")
                TimelineInfo("Duration", state.formattedDuration)
                TimelineInfo("Resolution", state.exportQuality)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Scene List
        Text("SCENE LIST", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp)
        
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(state.scenes) { index, scene ->
                SceneCard(
                    scene = scene,
                    index = index,
                    isSelected = state.selectedSceneIndex == index,
                    onSelect = { viewModel.selectScene(index) },
                    onDelete = { viewModel.deleteScene(index) },
                    onMoveUp = { if (index > 0) viewModel.moveScene(index, index - 1) },
                    onMoveDown = { if (index < state.scenes.size - 1) viewModel.moveScene(index, index + 1) },
                    onTrimStart = { viewModel.showTrimDialog(index, true) },
                    onTrimEnd = { viewModel.showTrimDialog(index, false) },
                    onAddText = { viewModel.showAddTextDialog(index) },
                    onAddTransition = { viewModel.showTransitionDialog(index) }
                )
            }
            
            // Add Scene Button
            item {
                Button(
                    onClick = { viewModel.addScene() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Add, null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADD SCENE", color = Color(0xFF4CAF50))
                }
            }
        }
        
        // Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1A1A1A),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton("📝 TEXT", "Add text overlay") { viewModel.showAddTextDialog(state.selectedSceneIndex) }
                ActionButton("🎵 AUDIO", "Add background music") { viewModel.showAddAudioDialog() }
                ActionButton("🎬 TRANS", "Add transition") { viewModel.showTransitionDialog(state.selectedSceneIndex) }
                ActionButton("✂️ TRIM", "Trim scene") { viewModel.showTrimDialog(state.selectedSceneIndex, true) }
            }
        }
    }
    
    // Dialogs
    if (state.showTrimDialog) {
        TrimDialog(
            currentStart = state.trimStart,
            currentEnd = state.trimEnd,
            onApply = { start, end -> viewModel.applyTrim(start, end) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }
    
    if (state.showTextDialog) {
        TextOverlayDialog(
            onApply = { text, position -> viewModel.addTextOverlay(text, position) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }
    
    if (state.showTransitionDialog) {
        TransitionDialog(
            onApply = { transition -> viewModel.addTransition(transition) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }
    
    if (state.showAudioDialog) {
        AudioDialog(
            onApply = { audioFile -> viewModel.addAudio(audioFile) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }
    
    if (state.showExportDialog) {
        ExportDialog(
            onExport = { quality -> viewModel.startExport(quality) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }
}

@Composable
private fun TimelineInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SceneCard(
    scene: VideoScene,
    index: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onAddText: () -> Unit,
    onAddTransition: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFF1A1A1A)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(scene.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${scene.duration}s | ${scene.transition} | ${if (scene.hasText) "📝" else ""}${if (scene.hasAudio) "🎵" else ""}", color = Color.Gray, fontSize = 11.sp)
                }
                Row {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallActionChip("✂️ Trim", onTrimStart)
                SmallActionChip("📝 Text", onAddText)
                SmallActionChip("🎬 Trans", onAddTransition)
            }
        }
    }
}

@Composable
private fun SmallActionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF333333)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = Color.White)
    }
}

@Composable
private fun ActionButton(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Text(icon, fontSize = 20.sp)
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

// Dialogs are in separate files below
