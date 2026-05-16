package com.aura.ai.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isCompleted) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Aura AI",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (state.currentStep) {
                OnboardingStep.WELCOME -> "Your AI phone assistant"
                OnboardingStep.PERMISSIONS -> "We need a few permissions"
                OnboardingStep.API_SETUP -> "Let's set up your API key"
                OnboardingStep.GITHUB_SETUP -> "Connect GitHub (optional)"
                OnboardingStep.COMPLETED -> "All set!"
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { viewModel.skipOnboarding() }) {
                Text("Skip")
            }
            Button(onClick = { viewModel.nextStep() }) {
                Text(if (state.currentStep == OnboardingStep.GITHUB_SETUP) "Finish" else "Next")
            }
        }
    }
}
