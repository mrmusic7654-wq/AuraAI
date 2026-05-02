package com.aura.ai.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraBottomSheet(onDismissRequest: () -> Unit, title: String? = null, content: @Composable (ColumnScope.() -> Unit)) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            content()
        }
    }
}
