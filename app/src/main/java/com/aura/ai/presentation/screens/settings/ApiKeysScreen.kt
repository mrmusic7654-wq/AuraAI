package com.aura.ai.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.ai.data.local.ApiKeyManager
import com.aura.ai.data.local.ApiKeyManager.ApiType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(apiKeyManager: ApiKeyManager) {
    var selectedApi by remember { mutableStateOf<ApiType?>(null) }
    var keyValue by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val allKeys = remember { apiKeyManager.getAllKeys() }
    
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
    ) {
        // Header
        TopAppBar(
            title = { Text("🔑 API Keys", color = Color.White, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
        )
        
        // Status summary
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(apiKeyManager.getStatusReport(), color = Color.White, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        
        // Keys list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ApiType.values().toList()) { apiType ->
                val status = apiKeyManager.getKeyStatus(apiType)
                val key = allKeys[apiType]
                
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedApi = apiType
                        keyValue = key ?: ""
                        showDialog = true
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = when (status) {
                            ApiKeyManager.KeyStatus.ACTIVE -> Color(0xFF1B5E20).copy(alpha = 0.3f)
                            else -> Color(0xFF1A1A1A)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status icon
                        Text(
                            when (status) {
                                ApiKeyManager.KeyStatus.ACTIVE -> "✅"
                                ApiKeyManager.KeyStatus.NOT_SET -> "⬜"
                                ApiKeyManager.KeyStatus.EMPTY -> "⚠️"
                                ApiKeyManager.KeyStatus.INVALID -> "❌"
                            },
                            fontSize = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(apiType.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(apiType.description, color = Color.Gray, fontSize = 12.sp)
                            if (status == ApiKeyManager.KeyStatus.ACTIVE) {
                                Text("••••${key?.takeLast(4) ?: ""}", color = Color(0xFF4CAF50), fontSize = 11.sp)
                            }
                        }
                        
                        Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    
    // Edit dialog
    if (showDialog && selectedApi != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔑 ${selectedApi!!.displayName}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(selectedApi!!.description, color = Color.Gray, fontSize = 13.sp)
                    if (selectedApi!!.url.isNotEmpty()) {
                        Text("Get key: ${selectedApi!!.url}", color = Color(0xFF64B5F6), fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyValue,
                        onValueChange = { keyValue = it },
                        label = { Text("API Key", color = Color.Gray) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKeyManager.saveKey(selectedApi!!, keyValue.trim())
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        apiKeyManager.deleteKey(selectedApi!!)
                        showDialog = false
                    }) { Text("Delete", color = Color(0xFFEF4444)) }
                    TextButton(onClick = { showDialog = false }) { Text("Cancel", color = Color.Gray) }
                }
            }
        )
    }
} 
