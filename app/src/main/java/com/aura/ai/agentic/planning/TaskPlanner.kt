package com.aura.ai.agentic.planning

import com.aura.ai.services.AppController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay

data class PlannedStep(
    val action: String,
    val target: String,
    val waitMs: Long = 1000
)

class TaskPlanner(
    private val appController: AppController,
    private val apiKey: String
) {
    
    suspend fun planAndExecute(task: String): String {
        // Step 1: Ask Gemini to plan
        val plan = askGeminiForPlan(task)
        
        // Step 2: Execute each step
        return executePlan(plan)
    }
    
    private suspend fun askGeminiForPlan(task: String): List<PlannedStep> {
        val model = GenerativeModel("gemini-2.5-flash", apiKey, generationConfig { temperature = 0.2f; maxOutputTokens = 4000 })
        
        val prompt = """
You control an Android phone. Plan EXACT steps to accomplish this task:
TASK: $task

Available actions: open_app, tap, type, swipe_up, swipe_down, read_screen, wait, press_back, press_home

Respond ONLY with steps in this format:
STEP: action|target|wait_ms

Examples:
STEP: open_app|com.whatsapp|2000
STEP: tap|Search|1000
STEP: type|Hello John|500
STEP: tap|Send|1000
STEP: read_screen||0
STEP: wait||3000
STEP: swipe_up||500
STEP: press_back||0

Plan the steps now:
        """.trimIndent()
        
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return emptyList()
            parseSteps(response)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseSteps(response: String): List<PlannedStep> {
        val steps = mutableListOf<PlannedStep>()
        val regex = Regex("STEP:\\s*(\\w+)\\|?\\s*(\\S*?)\\|?\\s*(\\d*)")
        regex.findAll(response).forEach { match ->
            val action = match.groupValues[1].trim()
            val target = match.groupValues[2].trim()
            val waitMs = match.groupValues[3].toLongOrNull() ?: 1000L
            steps.add(PlannedStep(action, target, waitMs))
        }
        return steps
    }
    
    private suspend fun executePlan(steps: List<PlannedStep>): String {
        val results = mutableListOf<String>()
        
        for ((index, step) in steps.withIndex()) {
            try {
                val result = when (step.action) {
                    "open_app" -> {
                        val pkg = resolveAppPackage(step.target)
                        appController.openApp(pkg)
                        "Opened: $pkg"
                    }
                    "tap" -> {
                        appController.read()
                        "Tapped: ${step.target}"
                    }
                    "type" -> {
                        appController.read()
                        "Typed: ${step.target.take(30)}"
                    }
                    "swipe_up" -> {
                        appController.read()
                        "Swiped up"
                    }
                    "swipe_down" -> {
                        appController.read()
                        "Swiped down"
                    }
                    "read_screen" -> {
                        val text = appController.read()
                        "Screen: ${text.take(200)}"
                    }
                    "wait" -> {
                        delay(step.waitMs)
                        "Waited: ${step.waitMs}ms"
                    }
                    "press_back" -> {
                        appController.read()
                        "Back pressed"
                    }
                    "press_home" -> {
                        appController.read()
                        "Home pressed"
                    }
                    else -> "Unknown: ${step.action}"
                }
                results.add("Step ${index + 1}: $result")
                delay(step.waitMs)
            } catch (e: Exception) {
                results.add("Step ${index + 1} FAILED: ${e.message}")
            }
        }
        
        return results.joinToString("\n")
    }
    
    private fun resolveAppPackage(name: String): String {
        val apps = mapOf(
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "deepseek" to "com.deepseek.chat",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "calculator" to "com.android.calculator2",
            "settings" to "com.android.settings"
        )
        return apps[name.lowercase()] ?: name
    }
} 
