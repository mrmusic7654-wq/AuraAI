package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.theme.*
import com.jeziellago.compose.markdown.Markdown

@Composable
fun AgentScreen(viewModel: AgentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Fuchsia500.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Fuchsia500.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Psychology, null, tint = Fuchsia400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("AURA NEURAL", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Neural Presence Detected", style = MaterialTheme.typography.labelMedium, color = Fuchsia400)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Emerald500))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!msg.isUser) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Fuchsia500.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Psychology, null, tint = Fuchsia400, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(if (msg.isUser) 16.dp else 16.dp).let { if (msg.isUser) it.copy(topEnd = RoundedCornerShape(4.dp)) else it.copy(topStart = RoundedCornerShape(4.dp)) },
                        color = if (msg.isUser) Cyan500.copy(alpha = 0.15f) else Color(0xFF1E293B),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        if (msg.isUser) {
                            Text(msg.text, modifier = Modifier.padding(12.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Markdown(content = msg.text, Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                    if (msg.isUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Cyan500.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Cyan400, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { i -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Fuchsia500.copy(alpha = 0.5f))); Spacer(modifier = Modifier.width(4.dp)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesizing...", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF020617).copy(alpha = 0.95f), shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.input, onValueChange = { viewModel.updateInput(it) },
                    modifier = Modifier.weight(1f), placeholder = { Text("Interlink directive...", color = TextMuted) },
                    shape = RoundedCornerShape(24.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Cyan500, focusedBorderColor = Cyan500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.send() }, enabled = state.input.isNotBlank() && !state.loading, modifier = Modifier.size(48.dp).clip(CircleShape).background(if (state.input.isNotBlank()) Cyan500 else Color(0xFF334155))) {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
