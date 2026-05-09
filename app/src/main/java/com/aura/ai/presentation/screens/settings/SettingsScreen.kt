package com.aura.ai.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, Emerald500.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).background(Emerald500.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Security, null, tint = Emerald400, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Security Protocol", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
        Text("Configure Aura's neural link", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Gemini API Key", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Enter your neural access key", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.apiKey, onValueChange = { viewModel.updateApiKey(it); viewModel.clearSaved() },
                    placeholder = { Text("Paste key here...", color = TextMuted) },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextSecondary) } },
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Cyan500, focusedBorderColor = Cyan500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.saveApiKey() }, modifier = Modifier.fillMaxWidth(), enabled = state.apiKey.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Cyan500), shape = RoundedCornerShape(12.dp)) {
                    Text(if (state.saved) "✅ Key Saved" else "Save API Key", color = Color.White)
                }

                if (state.saved) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.restartApp() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Emerald500), shape = RoundedCornerShape(12.dp)) {
                        Text("Restart App Now", color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }) {
            Icon(Icons.Default.OpenInNew, null, tint = Cyan400, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get a free API key →", color = Cyan400)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("AURA AI v1.0 // Protocol ALPHA", style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}
