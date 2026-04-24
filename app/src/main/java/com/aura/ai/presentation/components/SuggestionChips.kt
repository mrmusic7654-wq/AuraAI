package com.aura.ai.presentation.screens.agent.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// Predefined suggestion sets
val commonSuggestions = listOf(
    "Open WhatsApp",
    "Send message to Mom",
    "Take a screenshot",
    "Open Settings",
    "Turn on WiFi",
    "Check notifications",
    "Open YouTube",
    "Search Google for..."
)

val developerSuggestions = listOf(
    "Create GitHub repo",
    "Generate Compose app",
    "Commit changes",
    "Build APK",
    "Deploy to Play Store",
    "Run tests"
)

val automationSuggestions = listOf(
    "Schedule daily backup",
    "Monitor Reddit for mentions",
    "Check email every hour",
    "Scrape job listings",
    "Generate weekly report"
)
