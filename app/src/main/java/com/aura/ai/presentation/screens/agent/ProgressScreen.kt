package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProgressDashboard(viewModel: AgentViewModel) {
    val state by viewModel.state.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 AURA STATUS", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Build status
            state.buildLoop?.let { build ->
                StatusRow("🔨 Build", build.buildStatus.name, 
                    if (build.buildStatus == BuildStatus.BUILD_SUCCESS) Color(0xFF4CAF50) else Color(0xFFFFC107))
                StatusRow("🔄 Attempt", "${build.attemptNumber}/${build.maxAttempts}", Color.White)
                StatusRow("🔧 Fixes", "${build.totalFixesApplied}", Color.White)
                if (build.buildUrl.isNotEmpty()) StatusRow("🔗", build.buildUrl.take(40) + "...", Color(0xFF64B5F6))
            }
            
            // Generation progress
            if (state.isGeneratingApp) {
                StatusRow("📝 Progress", state.generationProgress, Color(0xFF64B5F6))
            }
            
            // Active model
            StatusRow("🧠 Model", state.activeModel, Color(0xFFCE93D8))
            
            // API calls
            StatusRow("📡 API Calls", "${state.totalApiCalls}", Color.White)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = if (state.isGeneratingApp) 0.7f else 0f,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF64B5F6),
                trackColor = Color(0xFF333333)
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.width(80.dp))
        Text(value, color = color, fontWeight = FontWeight.Medium)
    }
}
