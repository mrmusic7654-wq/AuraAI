package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*

data class ModelInfo(
    val name: String,
    val displayName: String,
    val strength: String,
    val dailyRequests: Int,
    val dailyLimit: Int,
    val isInCooldown: Boolean,
    val isSelected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDashboard(
    models: List<ModelInfo>,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, null, tint = Fuchsia400, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Model Selection",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextMuted)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Select a model manually. Auto-switching disabled when manual model is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(models) { model ->
                    ModelCard(
                        model = model,
                        onSelect = { onModelSelected(model.name) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    onSelect: () -> Unit
) {
    val usagePercent = if (model.dailyLimit > 0) 
        (model.dailyRequests.toFloat() / model.dailyLimit.toFloat() * 100).toInt() 
    else 0
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (model.isSelected) Cyan500.copy(alpha = 0.15f) else Color(0xFF1E293B),
        border = if (model.isSelected) 
            ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(Cyan500, Fuchsia500))
            ) 
        else null,
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                model.isInCooldown -> Color(0xFFEF4444)
                                usagePercent > 75 -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        model.strength,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                
                if (model.isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = Cyan400, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Usage bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${model.dailyRequests} / ${model.dailyLimit} requests",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        "$usagePercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            usagePercent > 90 -> Color(0xFFEF4444)
                            usagePercent > 75 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { usagePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        usagePercent > 90 -> Color(0xFFEF4444)
                        usagePercent > 75 -> Color(0xFFF59E0B)
                        else -> Cyan500
                    },
                    trackColor = Color(0xFF334155),
                )
            }
            
            if (model.isInCooldown) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "⚠️ In cooldown",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}
