package com.aura.ai.presentation.screens.market

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

data class GigItem(val title: String, val price: String, val orders: Int, val rating: Float, val tags: List<String>, val accent: Color)

@Composable
fun MarketScreen() {
    val gigs = remember {
        listOf(
            GigItem("Build a Custom AI Agent", "$499", 127, 4.9f, listOf("AI", "Automation"), Cyan500),
            GigItem("Android App Development", "$899", 89, 4.8f, listOf("Kotlin", "Compose"), Fuchsia500),
            GigItem("AI Swarm Orchestration", "$1,499", 34, 5.0f, listOf("Swarm", "Multi-Agent"), Emerald500),
            GigItem("Web Scraping Automation", "$299", 213, 4.7f, listOf("Python", "Scraping"), Amber400),
            GigItem("GitHub Workflow CI/CD", "$199", 456, 4.9f, listOf("DevOps", "Actions"), Violet400),
            GigItem("Telegram Bot Development", "$249", 178, 4.8f, listOf("Bot", "API"), Blue400),
            GigItem("AI Model Fine-Tuning", "$799", 56, 4.6f, listOf("ML", "Fine-tuning"), Rose400),
            GigItem("Prompt Engineering Pack", "$99", 892, 4.9f, listOf("Prompts", "Templates"), Amber400)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Amber400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = Amber400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("AUGMENTATION MARKET", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("AI Services & Gigs // Verified Modules", style = MaterialTheme.typography.labelMedium, color = Amber400)
            }
        }

        // Stats
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarketStatCard("Total Sales", "$45.2K", Emerald400, Modifier.weight(1f))
            MarketStatCard("Active Gigs", "8", Cyan500, Modifier.weight(1f))
            MarketStatCard("Avg Rating", "4.8 ★", Amber400, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Gig list
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(gigs) { gig ->
                GigCard(gig)
            }
        }
    }
}

@Composable
fun MarketStatCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GigCard(gig: GigItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(gig.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Code, null, tint = gig.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(gig.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        gig.tags.forEach { tag ->
                            Surface(shape = RoundedCornerShape(6.dp), color = gig.accent.copy(alpha = 0.15f)) {
                                Text(tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = gig.accent)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(gig.price, style = MaterialTheme.typography.titleMedium, color = gig.accent, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Amber400, modifier = Modifier.size(14.dp))
                        Text(" ${gig.rating} (${gig.orders})", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}
