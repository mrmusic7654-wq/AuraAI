package com.aura.ai.services.intelligence

import com.aura.ai.services.AppController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig

data class PerceptionResult(
    val screenText: String,
    val visualDescription: String,
    val confidenceScore: Float,
    val detectedElements: List<String>,
    val source: String // "accessibility", "vision", "hybrid"
)

class MultiChannelPerception(
    private val appController: AppController,
    private val apiKey: String
) {
    
    suspend fun perceiveScreen(prompt: String = "What's on this screen?"): PerceptionResult {
        val textChannel = tryGetTextChannel()
        val visionChannel = tryGetVisionChannel(prompt)
        
        return if (textChannel != null && visionChannel != null) {
            PerceptionResult(
                screenText = textChannel,
                visualDescription = visionChannel,
                confidenceScore = 0.95f,
                detectedElements = extractElements(textChannel, visionChannel),
                source = "hybrid"
            )
        } else if (textChannel != null) {
            PerceptionResult(textChannel, textChannel, 0.7f, extractElements(textChannel), "accessibility")
        } else if (visionChannel != null) {
            PerceptionResult(visionChannel, visionChannel, 0.6f, emptyList(), "vision")
        } else {
            PerceptionResult("", "Cannot perceive screen", 0f, emptyList(), "none")
        }
    }
    
    private suspend fun tryGetTextChannel(): String? {
        return try {
            val text = appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
            if (text.isNotBlank() && text.length > 10) text else null
        } catch (e: Exception) { null }
    }
    
    private suspend fun tryGetVisionChannel(prompt: String): String? {
        return try {
            appController.analyzeScreen(apiKey, prompt)
        } catch (e: Exception) { null }
    }
    
    private fun extractElements(textChannel: String, visionChannel: String = ""): List<String> {
        val elements = mutableListOf<String>()
        
        // Extract buttons
        Regex("(?:button|btn|tap|click)[:\\s]*[\"']?([^\"'\\n]+)[\"']?", RegexOption.IGNORE_CASE)
            .findAll(textChannel + visionChannel).forEach { elements.add("Button: ${it.groupValues[1].trim()}") }
        
        // Extract text fields
        Regex("(?:input|text field|type)[:\\s]*[\"']?([^\"'\\n]+)[\"']?", RegexOption.IGNORE_CASE)
            .findAll(textChannel + visionChannel).forEach { elements.add("Input: ${it.groupValues[1].trim()}") }
        
        return elements.distinct()
    }
    
    fun getChannelHealth(): Map<String, Boolean> {
        return mapOf(
            "accessibility" to (tryGetTextChannel() != null),
            "vision" to apiKey.isNotBlank()
        )
    }
}
