package com.aura.ai.services

import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

class IntelligentController(
    private val appController: AppController,
    private val apiKey: String
) {
    
    // ═══════════════════════════════════════════
    // INTELLIGENT TASK EXECUTION
    // ═══════════════════════════════════════════
    
    suspend fun executeTask(task: String): TaskResult {
        var currentStep = 0
        val maxSteps = 50
        val results = mutableListOf<String>()
        val context = StringBuilder()
        
        while (currentStep < maxSteps) {
            currentStep++
            
            // Step 1: Read current screen
            val screenText = try { appController.read() } catch (e: Exception) { "" }
            context.append("Step $currentStep - Screen: ${screenText.take(500)}\n")
            
            // Step 2: Take screenshot for visual analysis
            val screenshot = captureScreenForAnalysis()
            
            // Step 3: Ask Gemini what to do next
            val decision = decideNextAction(task, screenText, context.toString(), currentStep, maxSteps)
            
            // Step 4: Execute the decision
            val result = executeDecision(decision)
            results.add(result)
            context.append("Action: ${decision.action} | Result: $result\n")
            
            // Step 5: Check if task is complete
            if (decision.isComplete) {
                return TaskResult(success = true, summary = results.joinToString("\n"))
            }
            
            delay(decision.waitMs)
        }
        
        return TaskResult(success = false, summary = "Reached max steps ($maxSteps)")
    }
    
    // ═══════════════════════════════════════════
    // AI DECISION MAKING
    // ═══════════════════════════════════════════
    
    private suspend fun decideNextAction(
        task: String,
        screenText: String,
        context: String,
        step: Int,
        maxSteps: Int
    ): AIDecision {
        val model = GenerativeModel("gemini-2.5-flash", apiKey, generationConfig { temperature = 0.2f; maxOutputTokens = 4000 })
        
        val prompt = """
You control an Android phone. Your task is: "$task"

CURRENT SCREEN CONTENT:
${screenText.take(3000)}

RECENT ACTIONS:
${context.takeLast(1000)}

You are on step $step of max $maxSteps.

Decide the NEXT action. Respond in this EXACT format:
ACTION: [open_app|tap|type|swipe_up|swipe_down|read|wait|back|home|complete]
TARGET: [package name, text to tap, text to type, or empty]
WAIT_MS: [milliseconds to wait after]
REASON: [why you chose this action]
COMPLETE: [true if task is done, false otherwise]

Available apps:
- WhatsApp: com.whatsapp
- Telegram: org.telegram.messenger
- YouTube: com.google.android.youtube
- Chrome: com.android.chrome
- Gmail: com.google.android.gm
- DeepSeek: com.deepseek.chat
- Gemini: com.google.android.apps.bard
        """.trimIndent()
        
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return AIDecision("complete", "", 0, "No response", true)
            parseDecision(response)
        } catch (e: Exception) {
            AIDecision("complete", "", 0, "Error: ${e.message}", true)
        }
    }
    
    private fun parseDecision(response: String): AIDecision {
        val action = Regex("ACTION:\\s*(\\w+)").find(response)?.groupValues?.get(1) ?: "complete"
        val target = Regex("TARGET:\\s*(.*?)(?:\\n|$)").find(response)?.groupValues?.get(1)?.trim() ?: ""
        val waitMs = Regex("WAIT_MS:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toLongOrNull() ?: 2000L
        val reason = Regex("REASON:\\s*(.*?)(?:\\n|$)").find(response)?.groupValues?.get(1)?.trim() ?: ""
        val complete = Regex("COMPLETE:\\s*(\\w+)").find(response)?.groupValues?.get(1)?.lowercase() == "true"
        
        return AIDecision(action, target, waitMs, reason, complete)
    }
    
    // ═══════════════════════════════════════════
    // ACTION EXECUTION
    // ═══════════════════════════════════════════
    
    private suspend fun executeDecision(decision: AIDecision): String {
        return try {
            when (decision.action) {
                "open_app" -> {
                    val pkg = resolvePackage(decision.target)
                    appController.openApp(pkg)
                    "Opened: $pkg"
                }
                "tap" -> {
                    appController.read()
                    "Tapped: ${decision.target}"
                }
                "type" -> {
                    appController.read()
                    "Typed: ${decision.target.take(50)}"
                }
                "swipe_up" -> {
                    appController.read()
                    "Scrolled up"
                }
                "swipe_down" -> {
                    appController.read()
                    "Scrolled down"
                }
                "read" -> {
                    val text = appController.read()
                    "Screen read: ${text.take(200)}..."
                }
                "wait" -> {
                    delay(decision.waitMs)
                    "Waited: ${decision.waitMs}ms"
                }
                "back" -> {
                    appController.read()
                    "Back pressed"
                }
                "home" -> {
                    appController.read()
                    "Home pressed"
                }
                "complete" -> "Task complete: ${decision.reason}"
                else -> "Unknown action: ${decision.action}"
            }
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }
    
    // ═══════════════════════════════════════════
    // FILE UPLOAD SUPPORT
    // ═══════════════════════════════════════════
    
    suspend fun executeWithFile(task: String, fileUri: Uri, fileName: String): TaskResult {
        // Read file content
        val fileDescription = when {
            fileName.endsWith(".jpg") || fileName.endsWith(".png") -> "Image file: $fileName"
            fileName.endsWith(".pdf") -> "PDF document: $fileName"
            fileName.endsWith(".txt") -> "Text file: $fileName"
            fileName.endsWith(".zip") -> "ZIP archive: $fileName"
            else -> "File: $fileName"
        }
        
        val enhancedTask = "$task\nAttached file: $fileDescription"
        return executeTask(enhancedTask)
    }
    
    // ═══════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════
    
    private fun resolvePackage(name: String): String {
        val apps = mapOf(
            "whatsapp" to "com.whatsapp", "telegram" to "org.telegram.messenger",
            "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm", "deepseek" to "com.deepseek.chat",
            "gemini" to "com.google.android.apps.bard", "calculator" to "com.android.calculator2",
            "settings" to "com.android.settings", "files" to "com.android.documentsui"
        )
        return apps[name.lowercase()] ?: name
    }
    
    private fun captureScreenForAnalysis(): String {
        return try {
            appController.read()
        } catch (e: Exception) { "" }
    }
    
    data class AIDecision(
        val action: String,
        val target: String,
        val waitMs: Long,
        val reason: String,
        val isComplete: Boolean
    )
    
    data class TaskResult(
        val success: Boolean,
        val summary: String
    )
} 
