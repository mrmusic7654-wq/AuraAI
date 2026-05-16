package com.aura.ai.utils.parser

import com.aura.ai.data.models.github.FileToCreate
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStructureParser @Inject constructor() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    fun parseFromJson(jsonString: String): List<FileToCreate> {
        return try {
            val cleanJson = extractJsonArray(jsonString)
            json.decodeFromString<List<FileToCreateDto>>(cleanJson)
                .map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun parseFromPrompt(prompt: String): AppStructure {
        val lines = prompt.lines()
        val structure = mutableMapOf<String, String>()
        var currentPath: String? = null
        val contentBuilder = StringBuilder()
        
        for (line in lines) {
            when {
                line.startsWith("###") -> {
                    // Save previous file
                    currentPath?.let { path ->
                        structure[path] = contentBuilder.toString().trim()
                    }
                    currentPath = line.removePrefix("###").trim()
                    contentBuilder.clear()
                }
                line.startsWith("```") -> {
                    // Skip code block markers
                }
                else -> {
                    if (currentPath != null) {
                        contentBuilder.appendLine(line)
                    }
                }
            }
        }
        
        // Save last file
        currentPath?.let { path ->
            structure[path] = contentBuilder.toString().trim()
        }
        
        val files = structure.map { (path, content) ->
            FileToCreate(path = path, content = content)
        }
        
        return AppStructure(files = files)
    }
    
    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            "[]"
        }
    }
}

data class AppStructure(
    val files: List<FileToCreate>,
    val name: String? = null,
    val packageName: String? = null
)

@kotlinx.serialization.Serializable
private data class FileToCreateDto(
    val path: String,
    val content: String
) {
    fun toDomain() = FileToCreate(path = path, content = content)
}
