package com.aura.ai.services

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipProcessor(private val context: Context) {
    
    // ═══════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════
    
    data class ChatArchive(
        val fullText: String,
        val codeBlocks: List<CodeBlock>,
        val steps: List<String>,
        val metadata: ArchiveMetadata
    )
    
    data class CodeBlock(
        val language: String,
        val fileName: String,
        val content: String,
        val lineCount: Int = 0,
        val sizeBytes: Int = 0
    )
    
    data class ArchiveMetadata(
        val sourceFiles: List<String> = emptyList(),
        val totalSizeBytes: Long = 0,
        val extractedTextLength: Int = 0,
        val extractionTimestamp: Long = System.currentTimeMillis()
    )
    
    data class ExtractionResult(
        val success: Boolean,
        val archive: ChatArchive? = null,
        val error: String = "",
        val warnings: List<String> = emptyList()
    )
    
    // ═══════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════
    
    fun processChatZip(uri: Uri): ExtractionResult {
        val warnings = mutableListOf<String>()
        
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ExtractionResult(false, error = "Cannot open file")
            
            val zipStream = ZipInputStream(inputStream)
            val fullText = StringBuilder()
            val sourceFiles = mutableListOf<String>()
            var totalSize = 0L
            
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    sourceFiles.add(name)
                    totalSize += entry.size
                    
                    when {
                        isTextFile(name) -> {
                            extractTextContent(zipStream, fullText, name, warnings)
                        }
                        isCodeFile(name) -> {
                            extractCodeFile(zipStream, name, warnings)
                        }
                        else -> {
                            warnings.add("Skipped unsupported file: $name")
                        }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            inputStream.close()
            
            val text = fullText.toString()
            
            if (text.isBlank()) {
                return ExtractionResult(false, error = "No readable text found in archive")
            }
            
            val codeBlocks = extractAllCodeBlocks(text, warnings)
            val steps = extractAllSteps(text)
            
            ExtractionResult(
                success = true,
                archive = ChatArchive(
                    fullText = text,
                    codeBlocks = codeBlocks,
                    steps = steps,
                    metadata = ArchiveMetadata(
                        sourceFiles = sourceFiles,
                        totalSizeBytes = totalSize,
                        extractedTextLength = text.length
                    )
                ),
                warnings = warnings
            )
        } catch (e: Exception) {
            ExtractionResult(false, error = "ZIP processing failed: ${e.message}")
        }
    }
    
    fun mapCodeBlocksToFiles(blocks: List<CodeBlock>): Map<String, String> {
        val files = linkedMapOf<String, String>()
        val usedPaths = mutableSetOf<String>()
        
        for (block in blocks) {
            val path = determineFilePath(block, usedPaths)
            if (path != null) {
                usedPaths.add(path)
                files[path] = block.content
            }
        }
        
        return files
    }
    
    fun validateArchive(archive: ChatArchive): List<String> {
        val issues = mutableListOf<String>()
        
        if (archive.codeBlocks.isEmpty()) {
            issues.add("No code blocks found in archive")
        }
        
        val files = mapCodeBlocksToFiles(archive.codeBlocks)
        
        if (!files.containsKey("build.gradle.kts") && !files.keys.any { it.endsWith("build.gradle.kts") }) {
            issues.add("Missing root build.gradle.kts")
        }
        
        if (!files.keys.any { it.contains("AndroidManifest.xml") }) {
            issues.add("Missing AndroidManifest.xml")
        }
        
        val kotlinFiles = files.keys.filter { it.endsWith(".kt") }
        if (kotlinFiles.isEmpty()) {
            issues.add("No Kotlin source files found")
        }
        
        return issues
    }
    
    // ═══════════════════════════════════════════
    // EXTRACTION METHODS
    // ═══════════════════════════════════════════
    
    private fun extractTextContent(
        zipStream: ZipInputStream,
        fullText: StringBuilder,
        fileName: String,
        warnings: MutableList<String>
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(zipStream))
            val content = reader.readText()
            if (content.isNotBlank()) {
                fullText.append("===SOURCE:$fileName===\n")
                fullText.append(content)
                fullText.append("\n===END SOURCE===\n\n")
            }
        } catch (e: Exception) {
            warnings.add("Failed to read: $fileName - ${e.message}")
        }
    }
    
    private fun extractCodeFile(
        zipStream: ZipInputStream,
        fileName: String,
        warnings: MutableList<String>
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(zipStream))
            val content = reader.readText()
            if (content.isNotBlank()) {
                // Code files are handled in extractAllCodeBlocks from full text
                // This method logs them for metadata
            }
        } catch (e: Exception) {
            warnings.add("Failed to read code file: $fileName - ${e.message}")
        }
    }
    
    private fun extractAllCodeBlocks(text: String, warnings: MutableList<String>): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        
        // Method 1: ===FILE:path=== format (Aura's native format)
        val filePattern = Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===")
        filePattern.findAll(text).forEach { match ->
            val path = match.groupValues[1].trim()
            val content = normalizeContent(match.groupValues[2])
            if (content.length > 20) {
                blocks.add(CodeBlock(
                    language = detectLanguage(path),
                    fileName = path,
                    content = content,
                    lineCount = content.lines().size,
                    sizeBytes = content.length
                ))
            }
        }
        
        // Method 2: Triple backtick code blocks (ChatGPT/DeepSeek/Claude format)
        if (blocks.isEmpty()) {
            val backtickPattern = Regex("```(\\w+)?\\s*\\n?([\\s\\S]*?)```")
            backtickPattern.findAll(text).forEach { match ->
                val lang = match.groupValues[1].ifBlank { "txt" }
                val content = normalizeContent(match.groupValues[2])
                if (content.length > 50) {
                    val fileName = extractFileNameFromContent(content, lang)
                    blocks.add(CodeBlock(
                        language = lang,
                        fileName = fileName,
                        content = content,
                        lineCount = content.lines().size,
                        sizeBytes = content.length
                    ))
                }
            }
        }
        
        // Method 3: Source files embedded with ===SOURCE:=== markers
        val sourcePattern = Regex("===SOURCE:(.+?)===\\s*([\\s\\S]*?)\\s*===END SOURCE===")
        sourcePattern.findAll(text).forEach { match ->
            val fileName = match.groupValues[1].trim()
            val content = normalizeContent(match.groupValues[2])
            if (content.length > 20 && isCodeFile(fileName)) {
                blocks.add(CodeBlock(
                    language = detectLanguage(fileName),
                    fileName = fileName,
                    content = content,
                    lineCount = content.lines().size,
                    sizeBytes = content.length
                ))
            }
        }
        
        if (blocks.isEmpty()) {
            warnings.add("No code blocks found in any supported format")
        }
        
        return blocks
    }
    
    private fun extractAllSteps(text: String): List<String> {
        val steps = mutableListOf<String>()
        
        // Numbered steps: "Step 1:", "Step 2:", etc.
        val stepPattern = Regex("(?:Step|STEP)\\s*(\\d+)[:\\s.-]\\s*(.*?)(?=(?:Step|STEP)\\s*\\d+|$)", RegexOption.DOT_MATCHES_ALL)
        stepPattern.findAll(text).forEach { match ->
            steps.add("Step ${match.groupValues[1]}: ${match.groupValues[2].trim().take(200)}")
        }
        
        // Bullet points
        if (steps.isEmpty()) {
            val bulletPattern = Regex("(?:^|\\n)\\s*[•\\-\\*\\d+\\.]\\s*(.*?)(?=\\n\\s*[•\\-\\*\\d+\\.]|$)", RegexOption.DOT_MATCHES_ALL)
            bulletPattern.findAll(text).forEach { match ->
                val step = match.groupValues[1].trim()
                if (step.length > 5) steps.add(step.take(200))
            }
        }
        
        // Numbered list
        if (steps.isEmpty()) {
            val numberedPattern = Regex("(?:^|\\n)\\s*(\\d+)\\.\\s*(.*?)(?=\\n\\s*\\d+\\.|$)", RegexOption.DOT_MATCHES_ALL)
            numberedPattern.findAll(text).forEach { match ->
                steps.add("${match.groupValues[1]}. ${match.groupValues[2].trim().take(200)}")
            }
        }
        
        return steps
    }
    
    // ═══════════════════════════════════════════
    // FILE PATH DETERMINATION
    // ═══════════════════════════════════════════
    
    private fun determineFilePath(block: CodeBlock, usedPaths: Set<String>): String? {
        // If the block already has a proper filename, use it
        if (block.fileName.isNotBlank() && block.fileName != "unknown.kt" && !block.fileName.startsWith("file_")) {
            return block.fileName
        }
        
        val content = block.content
        
        // Root build files
        if (content.contains("plugins {") && content.contains("apply false") && content.contains("com.android.application")) {
            return "build.gradle.kts"
        }
        
        // App build file
        if (content.contains("com.android.application") && content.contains("compileSdk") && content.contains("namespace")) {
            return "app/build.gradle.kts"
        }
        
        // Settings file
        if (content.contains("pluginManagement") && content.contains("include(\":app\")")) {
            return "settings.gradle.kts"
        }
        
        // Gradle properties
        if (content.contains("android.useAndroidX") && content.contains("org.gradle.jvmargs")) {
            return "gradle.properties"
        }
        
        // Wrapper properties
        if (content.contains("distributionUrl") && content.contains("gradle")) {
            return "gradle/wrapper/gradle-wrapper.properties"
        }
        
        // Android Manifest
        if (content.contains("<manifest") && content.contains("<application")) {
            return "app/src/main/AndroidManifest.xml"
        }
        
        // Resource files
        if (content.contains("<resources>")) {
            return when {
                content.contains("<color") -> "app/src/main/res/values/colors.xml"
                content.contains("<string") -> "app/src/main/res/values/strings.xml"
                content.contains("<style") -> "app/src/main/res/values/themes.xml"
                else -> "app/src/main/res/values/resources.xml"
            }
        }
        
        // Kotlin source files
        if (content.contains("package ") && (content.contains("class ") || content.contains("object ") || content.contains("fun "))) {
            val pkg = Regex("package\\s+([\\w.]+)").find(content)?.groupValues?.get(1) ?: return null
            val cls = Regex("(?:class|object|interface|enum class)\\s+(\\w+)").find(content)?.groupValues?.get(1)
            if (cls != null) {
                val path = "app/src/main/java/${pkg.replace(".", "/")}/${cls}.kt"
                return if (path !in usedPaths) path else "${path.removeSuffix(".kt")}_copy.kt"
            }
        }
        
        // Shell scripts
        if (block.language in listOf("sh", "bash", "shell")) {
            return "codespace_setup.sh"
        }
        
        // GitHub workflow
        if (content.contains("jobs:") && content.contains("runs-on:") && content.contains("steps:")) {
            return ".github/workflows/build.yml"
        }
        
        return null
    }
    
    // ═══════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════
    
    private fun isTextFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".txt") || lower.endsWith(".md") || 
               lower.endsWith(".html") || lower.endsWith(".htm") ||
               lower.endsWith(".json") || lower.endsWith(".xml") ||
               lower.endsWith(".csv") || lower.endsWith(".log")
    }
    
    private fun isCodeFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".kt") || lower.endsWith(".kts") || 
               lower.endsWith(".java") || lower.endsWith(".xml") ||
               lower.endsWith(".gradle") || lower.endsWith(".properties") ||
               lower.endsWith(".sh") || lower.endsWith(".py") ||
               lower.endsWith(".yml") || lower.endsWith(".yaml")
    }
    
    private fun detectLanguage(fileName: String): String {
        return when {
            fileName.endsWith(".kt") -> "kotlin"
            fileName.endsWith(".kts") -> "kotlin"
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".xml") -> "xml"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".sh") -> "bash"
            fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> "yaml"
            fileName.endsWith(".json") -> "json"
            fileName.endsWith(".md") -> "markdown"
            fileName.endsWith(".html") -> "html"
            else -> "text"
        }
    }
    
    private fun extractFileNameFromContent(content: String, language: String): String {
        // Try package + class name
        val pkg = Regex("package\\s+([\\w.]+)").find(content)
        val cls = Regex("(?:class|object|interface|enum class)\\s+(\\w+)").find(content)
        if (pkg != null && cls != null) {
            return "${pkg.groupValues[1].replace(".", "/")}/${cls.groupValues[1]}.kt"
        }
        
        // Try explicit file marker
        Regex("(?:FILE:|file:|File:)\\s*(\\S+)").find(content)?.let {
            return it.groupValues[1]
        }
        
        return "unknown.${language}"
    }
    
    private fun normalizeContent(content: String): String {
        return content
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }
    
    fun getSupportedFormats(): List<String> {
        return listOf(
            "===FILE:path=== content ===END===",
            "```language content ```",
            "===SOURCE:path=== content ===END SOURCE===",
            "Plain text files (.txt, .md, .html, .json)",
            "DeepSeek chat exports",
            "ChatGPT chat exports",
            "Claude chat exports"
        )
    }
    
    fun getStats(archive: ChatArchive): Map<String, Any> {
        val files = mapCodeBlocksToFiles(archive.codeBlocks)
        return mapOf(
            "total_text_length" to archive.fullText.length,
            "code_blocks_found" to archive.codeBlocks.size,
            "files_mapped" to files.size,
            "steps_found" to archive.steps.size,
            "source_files" to archive.metadata.sourceFiles.size,
            "total_size_bytes" to archive.metadata.totalSizeBytes,
            "kotlin_files" to files.keys.count { it.endsWith(".kt") },
            "xml_files" to files.keys.count { it.endsWith(".xml") },
            "build_files" to files.keys.count { it.endsWith(".kts") || it.endsWith(".gradle") },
            "resource_files" to files.keys.count { it.contains("/res/") }
        )
    }
}
