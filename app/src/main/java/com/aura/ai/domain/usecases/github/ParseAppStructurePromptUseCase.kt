package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.FileToCreate
import com.aura.ai.data.remote.datasource.GeminiRemoteDataSource
import javax.inject.Inject

class ParseAppStructurePromptUseCase @Inject constructor(
    private val geminiDataSource: GeminiRemoteDataSource
) {
    
    suspend operator fun invoke(userPrompt: String): Result<List<FileToCreate>> {
        val prompt = """
            You are an AI that generates Android app file structures.
            Based on the user's request, generate a list of files to create in a GitHub repository.
            
            User request: "$userPrompt"
            
            Return a JSON array of files with path and content.
            Each file should have: "path", "content"
            
            Example:
            [
                {"path": "app/build.gradle.kts", "content": "plugins { id('com.android.application') }"},
                {"path": "app/src/main/AndroidManifest.xml", "content": "<?xml version='1.0'?><manifest>..."}
            ]
        """.trimIndent()
        
        return geminiDataSource.generateResponse(prompt)
            .mapCatching { response ->
                // Parse JSON response into FileToCreate list
                parseFileStructure(response)
            }
    }
    
    private fun parseFileStructure(jsonResponse: String): List<FileToCreate> {
        // Implementation would parse JSON
        return emptyList()
    }
}
