package com.aura.ai.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.theme.*

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("Cyberpunk Dark") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Header
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .border(2.dp, Emerald500.copy(alpha = 0.5f), CircleShape)
                .background(Emerald500.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Shield, null, tint = Emerald400, modifier = Modifier.size(40.dp)) }
        Spacer(modifier = Modifier.height(12.dp))
        Text("PROTOCOL", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
        Text("System Configuration // Security Profile", style = MaterialTheme.typography.labelMedium, color = Emerald400)
        Spacer(modifier = Modifier.height(28.dp))

        // ===== API KEY SECTION =====
        SectionCard("Neural Access Key", Icons.Default.Key, Cyan500) {
            OutlinedTextField(
                value = state.apiKey, onValueChange = { viewModel.updateApiKey(it); viewModel.clearSaved() },
                placeholder = { Text("Paste your Gemini API key...", color = TextMuted) },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextSecondary) } },
                colors = OutlinedTextFieldDefaults.colors(cursorColor = Cyan500, focusedBorderColor = Cyan500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveApiKey() }, enabled = state.apiKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text(if (state.saved) "Saved" else "Save", color = Color.White)
                }
                if (state.saved) {
                    Button(onClick = { viewModel.restartApp() },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text("Restart", color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }) {
                Icon(Icons.Default.OpenInNew, null, tint = Cyan400, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Get free API key →", color = Cyan400, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== THEME SECTION =====
        SectionCard("Interface Theme", Icons.Default.Palette, Fuchsia500) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cyberpunk Dark", "Matrix Green", "Neon Void").forEach { theme ->
                    FilterChip(selected = selectedTheme == theme, onClick = { selectedTheme = theme },
                        label = { Text(theme, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Fuchsia500.copy(alpha = 0.3f)))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== AGENTS SECTION =====
        SectionCard("Connected Agents", Icons.Default.Hub, Emerald500) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Active Swarm Agents", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("5", style = MaterialTheme.typography.titleMedium, color = Emerald400, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total API Requests Today", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("0 / 7500", style = MaterialTheme.typography.titleMedium, color = Cyan400, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== PERMISSIONS SECTION =====
        SectionCard("Permissions", Icons.Default.Security, Rose400) {
            PermissionRow("Accessibility Service", "Required", true)
            PermissionRow("Notifications", "Required", true)
            PermissionRow("Storage Access", "Granted", true)
            PermissionRow("Overlay", "Optional", false)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== SYSTEM SECTION =====
        SectionCard("System", Icons.Default.Settings, TextMuted) {
            SettingsRow("App Version", "v2.0.0")
            SettingsRow("Build", "NEXUS-CORE-263")
            SettingsRow("Gemini Model", "gemini-2.5-flash")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("AURA AI // PROTOCOL ACTIVE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun PermissionRow(name: String, status: String, granted: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Surface(shape = RoundedCornerShape(6.dp), color = (if (granted) Emerald500 else Rose400).copy(alpha = 0.15f)) {
            Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = if (granted) Emerald400 else Rose400)
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
