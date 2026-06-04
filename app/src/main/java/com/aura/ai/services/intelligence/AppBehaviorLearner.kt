package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class LearnedBehavior(
    val packageName: String,
    val tapPatterns: Map<String, List<TapRecord>> = emptyMap(),
    val responseTimeHistory: List<Long> = emptyList(),
    val successfulActions: Int = 0,
    val failedActions: Int = 0
)

data class TapRecord(
    val x: Float, val y: Float, val screenText: String, val timestamp: Long
)

class AppBehaviorLearner(context: Context) {
    
    private val learnDir = File(context.filesDir, "learned_behaviors")
    private val behaviors = mutableMapOf<String, LearnedBehavior>()
    
    init {
        learnDir.mkdirs()
        loadBehaviors()
    }
    
    fun recordTap(packageName: String, x: Float, y: Float, screenContext: String) {
        val current = behaviors.getOrPut(packageName) { LearnedBehavior(packageName) }
val mutablePatterns = current.tapPatterns.toMutableMap()
val existingList = mutablePatterns.getOrPut(screenContext.take(50)) { mutableListOf<TapRecord>() }.toMutableList()
existingList.add(TapRecord(x, y, screenContext, System.currentTimeMillis()))
mutablePatterns[screenContext.take(50)] = existingList
behaviors[packageName] = current.copy(tapPatterns = mutablePatterns)
        if (pattern.size > 10) (pattern as MutableList).removeAt(0)
        saveBehaviors()
    }
    
    fun predictTap(packageName: String, screenContext: String): Pair<Float, Float>? {
        val behavior = behaviors[packageName] ?: return null
        val bestMatch = behavior.tapPatterns.entries
            .filter { similarity(it.key, screenContext) > 0.6f }
            .maxByOrNull { similarity(it.key, screenContext) }
        
        return bestMatch?.value?.lastOrNull()?.let { Pair(it.x, it.y) }
    }
    
    fun recordResponseTime(packageName: String, timeMs: Long) {
        val current = behaviors.getOrPut(packageName) { LearnedBehavior(packageName) }
        val history = (current.responseTimeHistory as MutableList)
        history.add(timeMs)
        if (history.size > 50) history.removeAt(0)
        saveBehaviors()
    }
    
    fun getExpectedResponseTime(packageName: String): Long {
        val behavior = behaviors[packageName] ?: return 5000
        return if (behavior.responseTimeHistory.isEmpty()) 5000
        else behavior.responseTimeHistory.average().toLong()
    }
    
    fun recordSuccess(packageName: String) {
        val current = behaviors.getOrPut(packageName) { LearnedBehavior(packageName) }
        behaviors[packageName] = current.copy(successfulActions = current.successfulActions + 1)
        saveBehaviors()
    }
    
    fun recordFailure(packageName: String) {
        val current = behaviors.getOrPut(packageName) { LearnedBehavior(packageName) }
        behaviors[packageName] = current.copy(failedActions = current.failedActions + 1)
        saveBehaviors()
    }
    
    fun getReliabilityScore(packageName: String): Float {
        val behavior = behaviors[packageName] ?: return 0.5f
        val total = behavior.successfulActions + behavior.failedActions
        return if (total > 0) behavior.successfulActions.toFloat() / total else 0.5f
    }
    
    private fun similarity(a: String, b: String): Float {
        val aWords = a.lowercase().split("\\s+".toRegex()).toSet()
        val bWords = b.lowercase().split("\\s+".toRegex()).toSet()
        if (aWords.isEmpty() || bWords.isEmpty()) return 0f
        val intersection = aWords.intersect(bWords).size
        val union = aWords.union(bWords).size
        return intersection.toFloat() / union
    }
    
    private fun loadBehaviors() {
        val file = File(learnDir, "behaviors.json")
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            json.keys().forEach { pkg ->
                val b = json.getJSONObject(pkg)
                behaviors[pkg] = LearnedBehavior(
                    packageName = pkg,
                    responseTimeHistory = b.optJSONArray("responseTimes")?.let { arr ->
                        (0 until arr.length()).map { arr.getLong(it) }
                    } ?: emptyList(),
                    successfulActions = b.optInt("successes"),
                    failedActions = b.optInt("failures")
                )
            }
        } catch (e: Exception) { }
    }
    
    private fun saveBehaviors() {
        val json = JSONObject()
        behaviors.forEach { (pkg, behavior) ->
            if (behavior.successfulActions + behavior.failedActions > 0) {
                json.put(pkg, JSONObject().apply {
                    put("successes", behavior.successfulActions)
                    put("failures", behavior.failedActions)
                })
            }
        }
        File(learnDir, "behaviors.json").writeText(json.toString())
    }
}
