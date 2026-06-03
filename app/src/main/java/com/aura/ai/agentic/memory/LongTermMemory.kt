package com.aura.ai.agentic.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LongTermMemory(context: Context) {
    
    private val memoryDir = File(context.filesDir, "agentic_memory")
    private val memoryFile = File(memoryDir, "long_term_memory.json")
    private val memories = mutableMapOf<String, MemoryEntry>()
    
    data class MemoryEntry(
        val key: String,
        val value: String,
        val category: String,
        val importance: Float,  // 0-1, how important to remember
        val timestamp: Long = System.currentTimeMillis(),
        val accessCount: Int = 0
    )
    
    init {
        memoryDir.mkdirs()
        loadMemories()
    }
    
    fun remember(key: String, value: String, category: String = "general", importance: Float = 0.5f) {
        val existing = memories[key]
        val newImportance = if (existing != null) (existing.importance + importance) / 2 else importance
        
        memories[key] = MemoryEntry(
            key = key,
            value = value,
            category = category,
            importance = newImportance.coerceIn(0f, 1f),
            accessCount = (existing?.accessCount ?: 0) + 1
        )
        
        if (memories.size > 500) {
            // Remove least important memories
            val toRemove = memories.entries
                .sortedBy { it.value.importance }
                .take(50)
                .map { it.key }
            toRemove.forEach { memories.remove(it) }
        }
        
        saveMemories()
    }
    
    fun recall(key: String): String? {
        val entry = memories[key] ?: return null
        memories[key] = entry.copy(accessCount = entry.accessCount + 1)
        return entry.value
    }
    
    fun recallSimilar(query: String): List<String> {
        val keywords = query.lowercase().split(" ").filter { it.length > 2 }
        return memories.entries
            .filter { (key, entry) ->
                keywords.any { key.lowercase().contains(it) || entry.value.lowercase().contains(it) }
            }
            .sortedByDescending { it.value.importance * it.value.accessCount }
            .take(5)
            .map { "[${it.value.category}] ${it.value.value}" }
    }
    
    fun rememberPreference(key: String, value: String) {
        remember("pref_$key", value, "preference", 0.9f)
    }
    
    fun rememberPattern(pattern: String) {
        val existing = memories.entries.find { it.value.value == pattern }
        val count = (existing?.value?.accessCount ?: 0) + 1
        val importance = if (count >= 5) 0.9f else if (count >= 3) 0.7f else 0.5f
        remember("pattern_${pattern.hashCode()}", pattern, "pattern", importance)
    }
    
    fun getPreferences(): Map<String, String> {
        return memories.entries
            .filter { it.value.category == "preference" }
            .associate { it.key.removePrefix("pref_") to it.value.value }
    }
    
    fun getImportantMemories(limit: Int = 10): List<MemoryEntry> {
        return memories.values
            .sortedByDescending { it.importance * it.accessCount }
            .take(limit)
    }
    
    private fun loadMemories() {
        if (!memoryFile.exists()) return
        try {
            val json = JSONObject(memoryFile.readText())
            json.keys().forEach { key ->
                val entry = json.getJSONObject(key)
                memories[key] = MemoryEntry(
                    key = key,
                    value = entry.getString("value"),
                    category = entry.getString("category"),
                    importance = entry.getDouble("importance").toFloat(),
                    timestamp = entry.optLong("timestamp", System.currentTimeMillis()),
                    accessCount = entry.optInt("accessCount", 0)
                )
            }
        } catch (e: Exception) { }
    }
    
    private fun saveMemories() {
        val json = JSONObject()
        memories.forEach { (key, entry) ->
            json.put(key, JSONObject().apply {
                put("value", entry.value)
                put("category", entry.category)
                put("importance", entry.importance.toDouble())
                put("timestamp", entry.timestamp)
                put("accessCount", entry.accessCount)
            })
        }
        memoryFile.writeText(json.toString())
    }
    
    fun getStats(): String {
        return "💾 Long-term memory: ${memories.size} entries, ${getPreferences().size} preferences learned"
    }
}
