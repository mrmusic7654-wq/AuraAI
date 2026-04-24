package com.aura.ai.presentation.screens.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportButton(
    isExporting: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onExport,
        modifier = modifier,
        enabled = !isExporting
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                Icons.Default.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isExporting) "Exporting..." else "Export")
    }
}
