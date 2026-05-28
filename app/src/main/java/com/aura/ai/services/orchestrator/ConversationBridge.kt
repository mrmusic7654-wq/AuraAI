package com.aura.ai.services.orchestrator

import org.json.JSONArray
import org.json.JSONObject

class ConversationBridge {
    
    data class ParsedTask(
        val taskName: String,
        val steps: List<TaskStep>,
        val confidence: Float,
        val rawResponse: String
    )
    
    fun parseGeminiResponse(response: String): ParsedTask? {
        val cleaned = response
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        return try {
            val json = JSONObject(cleaned)
            val taskName = json.getString("task")
            val stepsArray = json.getJSONArray("steps")
            val steps = mutableListOf<TaskStep>()
            
            for (i in 0 until stepsArray.length()) {
                val stepJson = stepsArray.getJSONObject(i)
                steps.add(TaskStep(
                    id = stepJson.getString("id"),
                    action = stepJson.getString("action"),
                    params = stepJson.optJSONObject("params")?.let { params ->
                        val map = mutableMapOf<String, String>()
                        params.keys().forEach { key -> map[key] = params.getString(key) }
                        map
                    } ?: emptyMap(),
                    timeoutMs = stepJson.optLong("timeoutMs", 60000),
                    retryCount = stepJson.optInt("retryCount", 3),
                    dependsOn = stepJson.optJSONArray("dependsOn")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    condition = stepJson.optString("condition", null),
                    fallbackStepId = stepJson.optString("fallbackStepId", null)
                ))
            }
            
            ParsedTask(taskName, steps, 0.9f, response)
        } catch (e: Exception) {
            parseTextResponse(response)
        }
    }
    
    private fun parseTextResponse(response: String): ParsedTask? {
        val steps = mutableListOf<TaskStep>()
        var taskName = "Unknown Task"
        
        val lines = response.lines()
        var stepIndex = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Task:", ignoreCase = true) ||
                trimmed.startsWith("I'll", ignoreCase = true) -> {
                    taskName = trimmed.removePrefix("Task:").trim()
                }
                trimmed.matches(Regex("\\d+\\..*")) -> {
                    val action = parseActionFromText(trimmed)
                    if (action != null) {
                        steps.add(TaskStep(
                            id = "step_$stepIndex",
                            action = action,
                            params = parseParamsFromText(trimmed)
                        ))
                        stepIndex++
                    }
                }
            }
        }
        
        return if (steps.isNotEmpty()) ParsedTask(taskName, steps, 0.6f, response) else null
    }
    
    private fun parseActionFromText(text: String): String? {
        return when {
            text.contains("open", ignoreCase = true) -> "open_app"
            text.contains("tap", ignoreCase = true) || text.contains("click", ignoreCase = true) -> "tap"
            text.contains("type", ignoreCase = true) || text.contains("write", ignoreCase = true) || text.contains("enter", ignoreCase = true) -> "type"
            text.contains("swipe", ignoreCase = true) || text.contains("scroll", ignoreCase = true) -> "swipe"
            text.contains("wait", ignoreCase = true) -> "wait_for"
            text.contains("read", ignoreCase = true) || text.contains("check", ignoreCase = true) -> "read_screen"
            text.contains("screenshot", ignoreCase = true) || text.contains("capture", ignoreCase = true) -> "screenshot"
            text.contains("back", ignoreCase = true) -> "press_back"
            text.contains("home", ignoreCase = true) -> "press_home"
            else -> null
        }
    }
    
    private fun parseParamsFromText(text: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        // Extract app name
        Regex("(?:open|launch|start)\\s+(\\w+)", RegexOption.IGNORE_CASE).find(text)?.let {
            params["package"] = it.groupValues[1]
        }
        
        // Extract tap target
        Regex("(?:tap|click)\\s+(?:on\\s+)?[\"']?([^\"']+)[\"']?", RegexOption.IGNORE_CASE).find(text)?.let {
            params["text"] = it.groupValues[1].trim()
        }
        
        // Extract type text
        Regex("(?:type|write|enter)\\s+[\"']?([^\"']+)[\"']?", RegexOption.IGNORE_CASE).find(text)?.let {
            params["text"] = it.groupValues[1].trim()
        }
        
        // Extract wait time
        Regex("(\\d+)\\s*(?:second|sec|s)", RegexOption.IGNORE_CASE).find(text)?.let {
            params["timeout"] = (it.groupValues[1].toLong() * 1000).toString()
        }
        
        return params
    }
    
    fun buildTaskPlan(parsed: ParsedTask): TaskPlan {
        return TaskPlan(
            taskId = java.util.UUID.randomUUID().toString(),
            taskName = parsed.taskName,
            steps = parsed.steps,
            metadata = mapOf("confidence" to parsed.confidence.toString())
        )
    }
    
    fun validatePlan(plan: TaskPlan): List<String> {
        val warnings = mutableListOf<String>()
        
        if (plan.steps.isEmpty()) {
            warnings.add("Task plan has no steps")
        }
        
        plan.steps.forEach { step ->
            if (step.action.isBlank()) {
                warnings.add("Step ${step.id} has no action")
            }
            if (step.timeoutMs < 1000) {
                warnings.add("Step ${step.id} timeout is very short")
            }
        }
        
        return warnings
    }
}
