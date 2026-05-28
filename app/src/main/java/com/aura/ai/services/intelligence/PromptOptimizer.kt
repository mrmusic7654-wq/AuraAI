package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class PromptTemplate(
    val name: String,
    val template: String,
    val variables: List<String>,
    val successRate: Float = 0f,
    val timesUsed: Int = 0,
    val avgResponseLength: Int = 0
)

class PromptOptimizer(context: Context) {
    
    private val promptDir = File(context.filesDir, "prompts")
    private val templates = mutableMapOf<String, PromptTemplate>()
    
    init {
        promptDir.mkdirs()
        loadBuiltInTemplates()
        loadCustomTemplates()
    }
    
    fun getTemplate(name: String, variables: Map<String, String> = emptyMap()): String {
        val template = templates[name] ?: return ""
        var result = template.template
        for ((key, value) in variables) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }
    
    fun recordTemplateUsage(name: String, success: Boolean, responseLength: Int) {
        val current = templates[name] ?: return
        val newSuccessRate = if (current.timesUsed > 0) {
            (current.successRate * current.timesUsed + (if (success) 1f else 0f)) / (current.timesUsed + 1)
        } else {
            if (success) 1f else 0f
        }
        templates[name] = current.copy(
            successRate = newSuccessRate,
            timesUsed = current.timesUsed + 1,
            avgResponseLength = ((current.avgResponseLength * current.timesUsed) + responseLength) / (current.timesUsed + 1)
        )
        saveCustomTemplates()
    }
    
    fun getBestTemplate(taskType: String): PromptTemplate? {
        return templates.values
            .filter { it.name.contains(taskType, ignoreCase = true) && it.timesUsed >= 3 }
            .maxByOrNull { it.successRate }
    }
    
    fun getTemplateRecommendation(taskType: String): String {
        val best = getBestTemplate(taskType)
        return if (best != null) {
            "📊 Best template for '$taskType': ${best.name} (${(best.successRate * 100).toInt()}% success, ${best.timesUsed} uses)"
        } else {
            "📊 No proven template for '$taskType' yet. Will use default."
        }
    }
    
    private fun loadBuiltInTemplates() {
        templates["code_generation"] = PromptTemplate(
            name = "code_generation",
            template = "Create a complete Android app: {{description}}. Package: {{package}}. Use Jetpack Compose with Material3. Generate ALL files in format:\n===FILE:path===\ncode\n===END===",
            variables = listOf("description", "package")
        )
        
        templates["debug_error"] = PromptTemplate(
            name = "debug_error",
            template = "Fix this Android build error:\n{{error}}\n\nReturn ONLY the corrected code in format:\n===FILE:path===\ncode\n===END===",
            variables = listOf("error")
        )
        
        templates["screen_analysis"] = PromptTemplate(
            name = "screen_analysis",
            template = "Analyze this Android screen. Task: {{task}}. What do you see? Is the task complete? What should be the next action?\n\nRespond: STATUS: [WAITING|COMPLETE|ERROR|ACTION_NEEDED]\nACTION: [none|extract_code|tap:text|type:text|retry]\nDETAILS: [description]",
            variables = listOf("task")
        )
        
        templates["app_planning"] = PromptTemplate(
            name = "app_planning",
            template = "Plan an Android app: {{name}} - {{description}}. List ALL files needed for a compilable project. Include build files, manifest, source files, resources.",
            variables = listOf("name", "description")
        )
        
        templates["repo_analysis"] = PromptTemplate(
            name = "repo_analysis",
            template = "Analyze this GitHub repository: {{owner}}/{{repo}}. Describe the architecture, key features, dependencies, and code quality.",
            variables = listOf("owner", "repo")
        )
    }
    
    private fun loadCustomTemplates() {
        val file = File(promptDir, "custom_templates.json")
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            json.keys().forEach { name ->
                val t = json.getJSONObject(name)
                templates[name] = PromptTemplate(
                    name = name,
                    template = t.getString("template"),
                    variables = t.optJSONArray("variables")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    successRate = t.optDouble("successRate", 0.0).toFloat(),
                    timesUsed = t.optInt("timesUsed", 0),
                    avgResponseLength = t.optInt("avgResponseLength", 0)
                )
            }
        } catch (e: Exception) { }
    }
    
    private fun saveCustomTemplates() {
        val json = JSONObject()
        templates.filter { it.value.timesUsed > 0 }.forEach { (name, template) ->
            json.put(name, JSONObject().apply {
                put("template", template.template)
                put("variables", org.json.JSONArray(template.variables))
                put("successRate", template.successRate.toDouble())
                put("timesUsed", template.timesUsed)
                put("avgResponseLength", template.avgResponseLength)
            })
        }
        File(promptDir, "custom_templates.json").writeText(json.toString())
    }
}
