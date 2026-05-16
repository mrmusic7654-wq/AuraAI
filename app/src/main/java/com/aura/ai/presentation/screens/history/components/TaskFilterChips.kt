package com.aura.ai.presentation.screens.history.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TaskFilterChips(
    selectedStatus: String?,
    onStatusSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val statuses = listOf(null, "PENDING", "EXECUTING", "COMPLETED", "FAILED", "PAUSED")

    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { status ->
            @OptIn(ExperimentalMaterial3Api::class)
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onStatusSelected(status) },
                label = { Text(status ?: "All") }
            )
        }
    }
}
