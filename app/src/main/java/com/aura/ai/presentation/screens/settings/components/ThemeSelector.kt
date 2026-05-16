package com.aura.ai.presentation.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ThemeMode(val value: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default")
}

@Composable
fun ThemeSelector(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "App Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.values().forEach { mode ->
                    @OptIn(ExperimentalMaterial3Api::class)
                    FilterChip(
                        selected = mode == selectedTheme,
                        onClick = { onThemeSelected(mode) },
                        label = { Text(mode.value) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
