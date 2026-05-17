package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(viewModel: AgentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Sync drawer state with ViewModel
    LaunchedEffect(state.showDrawer) {
        if (state.showDrawer) drawerState.open() else drawerState.close()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !state.loading && state.input.isBlank(),
        drawerContent = {
            SessionDrawer(
                viewModel = viewModel,
                onSessionSelected = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
        ) {
            // Top Bar with Menu + Model Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hamburger Menu Button
                IconButton(onClick = { viewModel.toggleDrawer() }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Sessions",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Aura Logo
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Fuchsia500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(Fuchsia500.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Fuchsia400,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title
                Column {
                    Text(
                        "AURA NEURAL",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Neural Presence Detected",
                        style = MaterialTheme.typography.labelMedium,
                        color = Fuchsia400
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Model Selector Chip
                Surface(
                    onClick = { viewModel.toggleModelDashboard() },
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.manualModelSelected) Cyan500.copy(alpha = 0.2f) else Color(0xFF334155)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.activeModel.replace("gemini-", "").take(12),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.manualModelSelected) Cyan400 else TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select Model",
                            tint = if (state.manualModelSelected) Cyan400 else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Online Status Indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Emerald500)
                )
            }

            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!msg.isUser) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Fuchsia500.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = Fuchsia400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Surface(
                            shape = if (msg.isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                            else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                            color = if (msg.isUser) Cyan500.copy(alpha = 0.15f) else Color(0xFF1E293B),
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(
                                msg.text,
                                modifier = Modifier.padding(12.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (msg.isUser) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Cyan500.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Cyan400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Loading Indicator
                if (state.loading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(3) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Fuchsia500.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Synthesizing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF020617).copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Interlink directive...", color = TextMuted) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            cursorColor = Cyan500,
                            focusedBorderColor = Cyan500,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.send() },
                        enabled = state.input.isNotBlank() && !state.loading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (state.input.isNotBlank()) Cyan500 else Color(0xFF334155))
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Dashboard Bottom Sheet
    if (state.showModelDashboard) {
        ModelDashboard(
            models = viewModel.getModelInfoList(),
            onModelSelected = { viewModel.selectModel(it) },
            onDismiss = { viewModel.toggleModelDashboard() }
        )
    }
}
