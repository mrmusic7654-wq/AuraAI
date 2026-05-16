package com.aura.ai.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AuraCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) { content() }
}
