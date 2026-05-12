package com.aura.ai.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuraDarkColorScheme = darkColorScheme(
    primary = Cyan500, secondary = Fuchsia500, tertiary = Emerald500,
    background = CyberBlack, surface = CyberDarkSurface, surfaceVariant = CyberSurface,
    onPrimary = Color.White, onSecondary = Color.White, onTertiary = Color.White,
    onBackground = TextPrimary, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
    outline = CyberBorder, outlineVariant = CyberBorder.copy(alpha = 0.5f)
)

@Composable
fun AuraCyberpunkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuraDarkColorScheme, typography = AuraTypography, content = content)
}
