package com.aura.ai.presentation.screens.vault

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*
import java.io.File

data class FileItem(val name: String, val isDirectory: Boolean, val size: Long, val lastModified: Long, val path: String)

@Composable
fun VaultScreen() {
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var files by remember { mutableStateOf(getFiles(currentPath)) }
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Indigo400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Indigo400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = Indigo400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("VAULT", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text(currentPath.takeLast(40), style = MaterialTheme.typography.labelMedium, color = Indigo400)
            }
            IconButton(onClick = {
                val parent = File(currentPath).parentFile
                if (parent != null && parent.exists()) { currentPath = parent.absolutePath; files = getFiles(currentPath) }
            }) { Icon(Icons.Default.ArrowUpward, null, tint = Indigo400) }
        }

        // Storage bar
        val storageInfo = getStorageInfo()
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Storage", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(storageInfo, style = MaterialTheme.typography.labelMedium, color = Indigo400)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File list
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(files) { file ->
                VaultFileRow(file, selectedFile == file, { selectedFile = if (selectedFile == file) null else file }) {
                    if (file.isDirectory) { currentPath = file.path; files = getFiles(currentPath); selectedFile = null }
                }
            }
        }
    }
}

@Composable
fun VaultFileRow(file: FileItem, selected: Boolean, onClick: () -> Unit, onDoubleClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Indigo400.copy(alpha = 0.1f) else Color.Transparent),
        onClick = onDoubleClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null,
                tint = if (file.isDirectory) Amber400 else Indigo400, modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(formatFileSize(file.size), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

fun getFiles(path: String): List<FileItem> {
    val dir = File(path)
    return if (dir.exists() && dir.isDirectory) {
        dir.listFiles()?.map { FileItem(it.name, it.isDirectory, it.length(), it.lastModified(), it.absolutePath) }
            ?.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
    } else emptyList()
}

fun getStorageInfo(): String {
    val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
    val available = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    return "${available}GB free / ${total}GB total"
}

fun formatFileSize(size: Long): String = when { size < 1024 -> "$size B"; size < 1024 * 1024 -> "${size / 1024} KB"; else -> "${size / (1024 * 1024)} MB" }
