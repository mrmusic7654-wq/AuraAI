package com.aura.ai.presentation.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTopBar(
    title: String,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
