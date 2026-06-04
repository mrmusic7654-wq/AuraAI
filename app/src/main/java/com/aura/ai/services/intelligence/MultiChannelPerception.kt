package com.aura.ai.services.intelligence

import com.aura.ai.services.AppController

class MultiChannelPerception(private val appController: AppController, private val apiKey: String) {
    suspend fun perceiveScreen(prompt: String = "What's on screen?"): String {
        return try { appController.read() } catch (e: Exception) { appController.analyzeScreen(apiKey, prompt) }
    }
}
