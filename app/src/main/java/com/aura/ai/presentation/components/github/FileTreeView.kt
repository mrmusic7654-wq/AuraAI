package com.aura.ai.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.data.models.github.GitHubContent
import com.aura.ai.data.models.github.ContentType

@Composable
fun FileTreeView(
    contents: List<GitHubContent>,
    onFileClick: (GitHubContent) -> Unit,
    onFolderClick: (GitHubContent) -> Unit
) {
    LazyColumn {
        items(contents) { item ->
            FileTreeItem(
                item = item,
                onFileClick = onFileClick,
                onFolderClick = onFolderClick
            )
            Divider()
        }
    }
}

@Composable
fun FileTreeItem(
    item: GitHubContent,
    onFileClick: (GitHubContent) -> Unit,
    onFolderClick: (GitHubContent) -> Unit,
    depth: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (item.type) {
                    ContentType.FILE -> onFileClick(item)
                    ContentType.DIR -> onFolderClick(item)
                    else -> {}
                }
            }
            .padding(
                start = (depth * 16 + 16).dp,
                top = 12.dp,
                bottom = 12.dp,
                end = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (item.type) {
                ContentType.DIR -> Icons.Default.Folder
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
