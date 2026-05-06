package com.aura.ai.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraColorScheme = darkColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF00BFA5),
    tertiary = Color(0xFFFF4081),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AuraAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        content = content
    )
}
