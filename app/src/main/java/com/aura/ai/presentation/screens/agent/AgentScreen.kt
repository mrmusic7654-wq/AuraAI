package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AgentScreen(viewModel: AgentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { msg ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isUser) Color(0xFF6750A4) else Color(0xFF2D2D2D),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1E1E1E), shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { viewModel.updateInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tell Aura what to do...", color = Color.Gray) },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.input.isNotBlank() && !state.loading
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF6750A4))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF6750A4))
                    }
                }
            }
        }
    }
}
