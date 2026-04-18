package com.aura.ai.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.R

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    if (state.isCompleted) {
        LaunchedEffect(Unit) {
            onComplete()
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Image(
            painter = painterResource(id = R.drawable.ic_aura_logo),
            contentDescription = "Aura AI Logo",
            modifier = Modifier.size(120.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Welcome to Aura AI",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (state.currentStep) {
            OnboardingStep.WELCOME -> WelcomeStep(
                onNext = { viewModel.nextStep() },
                onSkip = { viewModel.skipOnboarding() }
            )
            OnboardingStep.PERMISSIONS -> PermissionsStep(
                onNext = { viewModel.nextStep() }
            )
            OnboardingStep.API_SETUP -> ApiSetupStep(
                onApiKeySet = { apiKey ->
                    viewModel.setApiKey(apiKey)
                    viewModel.nextStep()
                },
                onSkip = { viewModel.nextStep() }
            )
            OnboardingStep.GITHUB_SETUP -> GitHubSetupStep(
                onComplete = { viewModel.completeOnboarding() },
                onSkip = { viewModel.completeOnboarding() }
            )
            OnboardingStep.COMPLETED -> {
                LaunchedEffect(Unit) {
                    onComplete()
                }
            }
        }
    }
}

@Composable
fun WelcomeStep(
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your AI Phone Assistant",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Aura can control your phone, automate tasks, and even create apps on GitHub.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
            
            Button(onClick = onNext) {
                Text("Get Started")
            }
        }
    }
}

@Composable
fun PermissionsStep(
    onNext: () -> Unit
) {
    val context = LocalContext.current
    
    Column {
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Accessibility Service",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Allows Aura to read screen content and perform actions",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Accessibility")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Overlay Permission",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Allows Aura to show controls over other apps",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Overlay")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun ApiSetupStep(
    onApiKeySet: (String) -> Unit,
    onSkip: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    
    Column {
        Text(
            text = "Gemini API Key",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Aura uses Gemini AI to understand your requests. Get a free API key from Google AI Studio.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(
            onClick = {
                // Open AI Studio
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://aistudio.google.com/app/apikey")
                )
                LocalContext.current.startActivity(intent)
            }
        ) {
            Text("Get a free API key →")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip for now")
            }
            
            Button(
                onClick = { onApiKeySet(apiKey) },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
fun GitHubSetupStep(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        Text(
            text = "GitHub Integration",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Connect GitHub to let Aura create repositories and apps for you.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip")
            }
            
            Button(
                onClick = onComplete,
                modifier = Modifier.weight(1f)
            ) {
                Text("Complete Setup")
            }
        }
    }
}
