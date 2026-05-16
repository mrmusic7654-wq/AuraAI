package com.aura.ai.data.remote.datasource

import com.aura.ai.data.remote.api.GeminiApi
import com.aura.ai.data.remote.dto.Content
import com.aura.ai.data.remote.dto.GeminiRequestDto
import com.aura.ai.data.remote.dto.GeminiResponseDto
import com.aura.ai.data.remote.dto.Part
import com.aura.ai.utils.constants.GeminiModels
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRemoteDataSource @Inject constructor(
    private val geminiApi: GeminiApi
) {
    
    suspend fun generateResponse(
        prompt: String,
        model: String = GeminiModels.FLASH,
        systemInstruction: String? = null
    ): Result<String> {
        return try {
            val contents = buildList {
                if (systemInstruction != null) {
                    add(Content(parts = listOf(Part(systemInstruction)), role = "system"))
                }
                add(Content(parts = listOf(Part(prompt)), role = "user"))
            }
            
            val request = GeminiRequestDto(
                contents = contents,
                generationConfig = null
            )
            
            val response = geminiApi.generateContent(
                model = model,
                request = request
            )
            
            response.text?.let {
                Result.success(it)
            } ?: Result.failure(Exception("No response text from Gemini"))
            
        } catch (e: Exception) {
            Timber.e(e, "Gemini API call failed")
            Result.failure(e)
        }
    }
    
    suspend fun generateStructuredResponse(
        prompt: String,
        jsonSchema: String,
        model: String = GeminiModels.FLASH
    ): Result<String> {
        val fullPrompt = """
            $prompt
            
            Respond with valid JSON matching this schema:
            $jsonSchema
            
            Response:
        """.trimIndent()
        
        return generateResponse(fullPrompt, model)
    }
}
