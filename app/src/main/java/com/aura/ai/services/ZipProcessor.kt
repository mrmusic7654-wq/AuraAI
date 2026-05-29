package com.aura.ai.services

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

class ZipProcessor(private val context: Context) {
    
    data class ChatArchive(
        val fullText: String,
        val codeBlocks: List<CodeBlock>,
        val steps: List<String>
    )
    
    data class CodeBlock(
        val language: String,
        val fileName: String,
        val content: String
    )
    
    fun processChatZip(uri: Uri): ChatArchive {
        val fullText = StringBuilder()
        val inputStream = context.contentResolver.openInputStream(uri)
        val zipStream = ZipInputStream(inputStream)
        
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name.lowercase()
                if (name.endsWith(".txt") || name.endsWith(".md") || 
                    name.endsWith(".html") || name.endsWith(".htm") ||
                    name.endsWith(".json")) {
                    fullText.append(zipStream.bufferedReader().readText())
                    fullText.append("\n\n")
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        
        zipStream.close()
        inputStream.close()
        
        val text = fullText.toString()
        return ChatArchive(
            fullText = text,
            codeBlocks = extractCodeBlocks(text),
            steps = extractSteps(text)
        )
    }
    
    private fun extractCodeBlocks(text: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        
        // Extract ``` code blocks
        Regex("```(\\w+)?\\s*\\n?([\\s\\S]*?)```").findAll(text).forEach { match ->
            val language = match.groupValues[1].ifBlank { "txt" }
            val content = match.groupValues[2].trim()
            if (content.length > 50) {
                blocks.add(CodeBlock(language, extractFileName(content, language), content))
            }
        }
        
        // Extract ===FILE:=== blocks
        if (blocks.isEmpty()) {
            Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(text).forEach { match ->
                val fileName = match.groupValues[1].trim()
                val content = match.groupValues[2].trim()
                if (content.length > 50) {
                    blocks.add(CodeBlock("kt", fileName, content))
                }
            }
        }
        
        return blocks
    }
    
    private fun extractFileName(content: String, language: String): String {
        // Try to find package + class name
        val pkg = Regex("package\\s+([\\w.]+)").find(content)
        val cls = Regex("(?:class|object|interface|enum class)\\s+(\\w+)").find(content)
        if (pkg != null && cls != null) {
            return "${pkg.groupValues[1].replace(".", "/")}/${cls.groupValues[1]}.kt"
        }
        
        // Try explicit file marker
        Regex("(?:FILE:|file:|File:)\\s*(\\S+)").find(content)?.let {
            return it.groupValues[1]
        }
        
        return "file_${System.currentTimeMillis()}.$language"
    }
    
    private fun extractSteps(text: String): List<String> {
        val steps = mutableListOf<String>()
        
        Regex("(?:Step|STEP)\\s*(\\d+)[:\\s]*(.*?)(?=(?:Step|STEP)\\s*\\d+|$)", 
            RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
            steps.add("Step ${match.groupValues[1]}: ${match.groupValues[2].trim()}")
        }
        
        if (steps.isEmpty()) {
            Regex("(?:^|\\n)\\s*(?:\\d+\\.|\\-|\\*)\\s*(.*?)(?=\\n\\s*(?:\\d+\\.|\\-|\\*)|$)", 
                RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { match ->
                steps.add(match.groupValues[1].trim())
            }
        }
        
        return steps
    }
    
    fun mapCodeBlocksToFiles(blocks: List<CodeBlock>): Map<String, String> {
        val files = mutableMapOf<String, String>()
        
        for (block in blocks) {
            val fileName = when {
                // Root build files
                block.content.contains("plugins {") && block.content.contains("apply false") -> 
                    "build.gradle.kts"
                    
                // App build file
                block.content.contains("com.android.application") && block.content.contains("compileSdk") -> 
                    "app/build.gradle.kts"
                    
                // Settings
                block.content.contains("pluginManagement") && block.content.contains("include") -> 
                    "settings.gradle.kts"
                    
                // Wrapper properties
                block.content.contains("distributionUrl") -> 
                    "gradle/wrapper/gradle-wrapper.properties"
                    
                // Gradle properties
                block.content.contains("android.useAndroidX") -> 
                    "gradle.properties"
                    
                // Android Manifest
                block.content.contains("<manifest") && block.content.contains("<application") -> 
                    "app/src/main/AndroidManifest.xml"
                    
                // Resource files
                block.content.contains("<resources>") && block.content.contains("<color") -> 
                    "app/src/main/res/values/colors.xml"
                block.content.contains("<resources>") && block.content.contains("<string") -> 
                    "app/src/main/res/values/strings.xml"
                block.content.contains("<resources>") && block.content.contains("<style") -> 
                    "app/src/main/res/values/themes.xml"
                    
                // Shell scripts
                block.language in listOf("sh", "bash", "shell") -> 
                    "codespace_setup.sh"
                    
                // Default: use the extracted filename
                else -> block.fileName
            }
            
            files[fileName] = block.content
        }
        
        return files
    }
}
