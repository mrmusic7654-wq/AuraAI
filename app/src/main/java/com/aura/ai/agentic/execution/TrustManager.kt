package com.aura.ai.agentic.execution

import android.content.Context
import org.json.JSONObject
import java.io.File

class TrustManager(context: Context) {
    
    enum class TrustLevel {
        ASK_FIRST,      // Always ask user before executing
        AUTO_APPROVE,   // Auto-execute but notify user
        FULL_AUTONOMY   // Execute without notification
    }
    
    data class TrustRule(
        val actionPattern: String,
        val trustLevel: TrustLevel,
        val timesApproved: Int = 0,
        val timesRejected: Int = 0
    )
    
    private val trustDir = File(context.filesDir, "trust")
    private val trustFile = File(trustDir, "trust_rules.json")
    private val rules = mutableListOf<TrustRule>()
    
    init {
        trustDir.mkdirs()
        loadRules()
    }
    
    fun getTrustLevel(action: String): TrustLevel {
        val matchingRule = rules.find { rule ->
            val pattern = rule.actionPattern.lowercase()
            action.lowercase().contains(pattern)
        }
        
        return when {
            matchingRule != null -> matchingRule.trustLevel
            action.contains("fix", ignoreCase = true) -> TrustLevel.AUTO_APPROVE
            action.contains("update dependency", ignoreCase = true) -> TrustLevel.AUTO_APPROVE
            action.contains("scan", ignoreCase = true) -> TrustLevel.AUTO_APPROVE
            action.contains("monitor", ignoreCase = true) -> TrustLevel.FULL_AUTONOMY
            action.contains("delete", ignoreCase = true) -> TrustLevel.ASK_FIRST
            action.contains("force", ignoreCase = true) -> TrustLevel.ASK_FIRST
            action.contains("generate app", ignoreCase = true) -> TrustLevel.ASK_FIRST
            action.contains("create app", ignoreCase = true) -> TrustLevel.ASK_FIRST
            action.contains("deploy", ignoreCase = true) -> TrustLevel.ASK_FIRST
            else -> TrustLevel.ASK_FIRST
        }
    }
    
    fun recordApproval(action: String) {
        updateRule(action, approved = true)
    }
    
    fun recordRejection(action: String) {
        updateRule(action, approved = false)
    }
    
    fun shouldAutoApprove(action: String): Boolean {
        val rule = rules.find { action.lowercase().contains(it.actionPattern.lowercase()) }
        if (rule == null) return false
        
        // Auto-approve if approved 5+ times and never rejected
        if (rule.timesApproved >= 5 && rule.timesRejected == 0) return true
        
        // Auto-approve if approval rate > 80% after 10+ decisions
        val total = rule.timesApproved + rule.timesRejected
        if (total >= 10) {
            val rate = rule.timesApproved.toFloat() / total
            if (rate > 0.8f) return true
        }
        
        return false
    }
    
    private fun updateRule(action: String, approved: Boolean) {
        val keywords = extractKeywords(action)
        val pattern = keywords.joinToString(" ")
        
        val existing = rules.find { it.actionPattern == pattern }
        if (existing != null) {
            val index = rules.indexOf(existing)
            rules[index] = existing.copy(
                timesApproved = existing.timesApproved + if (approved) 1 else 0,
                timesRejected = existing.timesRejected + if (!approved) 1 else 0
            )
        } else {
            rules.add(TrustRule(
                actionPattern = pattern,
                trustLevel = TrustLevel.ASK_FIRST,
                timesApproved = if (approved) 1 else 0,
                timesRejected = if (!approved) 1 else 0
            ))
        }
        
        saveRules()
    }
    
    private fun extractKeywords(action: String): List<String> {
        return action.lowercase()
            .replace("fix", "fix")
            .replace("create", "create")
            .replace("generate", "generate")
            .replace("deploy", "deploy")
            .replace("update", "update")
            .replace("delete", "delete")
            .split(" ")
            .filter { it.length > 3 }
            .take(3)
    }
    
    private fun loadRules() {
        if (!trustFile.exists()) return
        try {
            val json = JSONObject(trustFile.readText())
            val arr = json.getJSONArray("rules")
            for (i in 0 until arr.length()) {
                val rule = arr.getJSONObject(i)
                rules.add(TrustRule(
                    actionPattern = rule.getString("pattern"),
                    trustLevel = TrustLevel.valueOf(rule.getString("level")),
                    timesApproved = rule.optInt("approved", 0),
                    timesRejected = rule.optInt("rejected", 0)
                ))
            }
        } catch (e: Exception) { }
    }
    
    private fun saveRules() {
        val json = JSONObject()
        val arr = org.json.JSONArray()
        rules.forEach { rule ->
            arr.put(JSONObject().apply {
                put("pattern", rule.actionPattern)
                put("level", rule.trustLevel.name)
                put("approved", rule.timesApproved)
                put("rejected", rule.timesRejected)
            })
        }
        json.put("rules", arr)
        trustFile.writeText(json.toString())
    }
    
    fun getTrustReport(): String {
        val autoApproved = rules.count { it.trustLevel == TrustLevel.AUTO_APPROVE || it.trustLevel == TrustLevel.FULL_AUTONOMY }
        return "🔒 Trust: ${rules.size} rules, $autoApproved auto-approved"
    }
}
