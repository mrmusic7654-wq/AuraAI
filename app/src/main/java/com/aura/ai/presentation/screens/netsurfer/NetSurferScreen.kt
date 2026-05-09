package com.aura.ai.presentation.screens.netsurfer

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.aura.ai.presentation.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NetSurferScreen() {
    var url by remember { mutableStateOf("https://google.com") }
    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Blue400.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).background(Blue400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Language, null, tint = Blue400, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("NETSURFER", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
            if (isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Blue400, strokeWidth = 2.dp)
            }
        }

        // URL Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F172A)).border(1.dp, Blue400.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView?.goBack() }) { Icon(Icons.Default.ArrowBack, null, tint = Blue400, modifier = Modifier.size(20.dp)) }
            OutlinedTextField(
                value = inputUrl, onValueChange = { inputUrl = it },
                placeholder = { Text("Enter URL...", color = TextMuted) },
                modifier = Modifier.weight(1f), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(cursorColor = Blue400, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            IconButton(onClick = { webView?.goForward() }) { Icon(Icons.Default.ArrowForward, null, tint = Blue400, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { url = inputUrl.let { if (it.startsWith("http")) it else "https://$it" }; webView?.loadUrl(url) }) { Icon(Icons.Default.Search, null, tint = Blue400, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, null, tint = Blue400, modifier = Modifier.size(20.dp)) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WebView
        Card(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { isLoading = true; url?.let { inputUrl = it } }
                            override fun onPageFinished(view: WebView?, url: String?) { isLoading = false }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
