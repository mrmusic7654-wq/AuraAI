package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.Task
import com.aura.ai.data.remote.datasource.GeminiRemoteDataSource
import timber.log.Timber
import javax.inject.Inject

class ParseUserIntentUseCase @Inject constructor(
    private val geminiDataSource: GeminiRemoteDataSource
) {
    
    suspend operator fun invoke(userInput: String): ParseResult {
        return try {
            val prompt = """
                Parse the following user request into a structured task.
                Identify:
                1. The primary action (OPEN, SEARCH, SEND, NAVIGATE, etc.)
                2. The target app (if mentioned)
                3. Any specific content (message text, search query, etc.)
                4. Required steps
                
                User request: "$userInput"
                
                Respond in this format:
                ACTION: [primary action]
                APP: [app name or null]
                CONTENT: [content or null]
                STEPS: [comma-separated steps]
            """.trimIndent()
            
            val response = geminiDataSource.generateResponse(prompt)
            
            response.fold(
                onSuccess = { text ->
                    parseStructuredResponse(text, userInput)
                },
                onFailure = {
                    ParseResult.Fallback(userInput)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse intent")
            ParseResult.Fallback(userInput)
        }
    }
    
    private fun parseStructuredResponse(text: String, originalInput: String): ParseResult {
        val lines = text.lines()
        var action = "UNKNOWN"
        var app: String? = null
        var content: String? = null
        var steps = emptyList<String>()
        
        lines.forEach { line ->
            when {
                line.startsWith("ACTION:") -> action = line.substringAfter("ACTION:").trim()
                line.startsWith("APP:") -> app = line.substringAfter("APP:").trim().takeIf { it != "null" }
                line.startsWith("CONTENT:") -> content = line.substringAfter("CONTENT:").trim().takeIf { it != "null" }
                line.startsWith("STEPS:") -> steps = line.substringAfter("STEPS:").trim().split(",").map { it.trim() }
            }
        }
        
        return ParseResult.Success(
            action = action,
            targetApp = app,
            content = content,
            steps = steps.ifEmpty { listOf(originalInput) }
        )
    }
}

sealed class ParseResult {
    data class Success(
        val action: String,
        val targetApp: String?,
        val content: String?,
        val steps: List<String>
    ) : ParseResult()
    
    data class Fallback(val originalInput: String) : ParseResult()
}
