package com.aura.ai.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun TemplateSelectorChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
