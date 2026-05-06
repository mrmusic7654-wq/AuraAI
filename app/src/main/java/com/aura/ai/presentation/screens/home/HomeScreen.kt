package com.aura.ai.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Aura AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Your AI phone assistant is ready.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Text("1. Go to Settings → Add your Gemini API key", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text("2. Restart the app", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text("3. Open Agent tab → Start chatting!", style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
