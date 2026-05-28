package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class CachedResponse(
    val inputHash: String,
    val response: String,
    val timestamp: Long,
    val hitCount: Int = 0
)

class OfflineCapabilityManager(context: Context) {
    
    private val cacheDir = File(context.filesDir, "offline_cache")
    private val cacheFile = File(cacheDir, "response_cache.json")
    private val cache = mutableMapOf<String, CachedResponse>()
    private val maxCacheSize = 100
    
    init {
        cacheDir.mkdirs()
        loadCache()
    }
    
    fun findCachedResponse(input: String): String? {
        val hash = simpleHash(input.take(200))
        val cached = cache[hash] ?: return null
        cache[hash] = cached.copy(hitCount = cached.hitCount + 1)
        return cached.response
    }
    
    fun cacheResponse(input: String, response: String) {
        val hash = simpleHash(input.take(200))
        cache[hash] = CachedResponse(hash, response, System.currentTimeMillis())
        if (cache.size > maxCacheSize) {
            val oldest = cache.minByOrNull { it.value.timestamp }
            oldest?.let { cache.remove(it.key) }
        }
        saveCache()
    }
    
    fun getCommonActions(): List<String> {
        return cache.values
            .filter { it.hitCount >= 3 }
            .sortedByDescending { it.hitCount }
            .take(10)
            .map { it.response.take(100) }
    }
    
    fun getOfflineCapabilityLevel(): String {
        val hitCount = cache.values.sumOf { it.hitCount }
        return when {
            hitCount > 50 -> "HIGH - Can handle most common tasks offline"
            hitCount > 20 -> "MEDIUM - Limited offline capability"
            hitCount > 0 -> "LOW - Very limited offline capability"
            else -> "NONE - No offline capability"
        }
    }
    
    fun canHandleOffline(input: String): Boolean {
        return findCachedResponse(input) != null
    }
    
    fun getCacheStats(): String {
        return "📦 Cache: ${cache.size}/$maxCacheSize entries, ${cache.values.sumOf { it.hitCount }} total hits"
    }
    
    private fun simpleHash(input: String): String {
        var hash = 0
        for (c in input) { hash = hash * 31 + c.code }
        return hash.toUInt().toString(16)
    }
    
    private fun loadCache() {
        if (!cacheFile.exists()) return
        try {
            val json = JSONObject(cacheFile.readText())
            json.keys().forEach { hash ->
                val c = json.getJSONObject(hash)
                cache[hash] = CachedResponse(
                    inputHash = hash,
                    response = c.getString("response"),
                    timestamp = c.optLong("timestamp", 0),
                    hitCount = c.optInt("hitCount", 0)
                )
            }
        } catch (e: Exception) { }
    }
    
    private fun saveCache() {
        val json = JSONObject()
        cache.forEach { (hash, response) ->
            json.put(hash, JSONObject().apply {
                put("response", response.response)
                put("timestamp", response.timestamp)
                put("hitCount", response.hitCount)
            })
        }
        cacheFile.writeText(json.toString())
    }
    
    fun clearCache() {
        cache.clear()
        cacheFile.delete()
    }
}
