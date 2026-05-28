package com.aura.ai.services.orchestrator

import android.content.Context
import org.json.JSONObject
import java.io.File

data class TaskMetrics(
    val taskId: String,
    val taskName: String,
    val totalSteps: Int,
    val completedSteps: Int,
    val failedSteps: Int,
    val totalDurationMs: Long,
    val retriesUsed: Int,
    val success: Boolean
)

data class StepMetrics(
    val stepId: String,
    val action: String,
    val status: String,
    val durationMs: Long,
    val retriesUsed: Int,
    val timestamp: Long
)

class AuraMetrics(private val context: Context) {
    
    private val metricsDir = File(context.filesDir, "metrics")
    private val dailyMetricsFile = File(metricsDir, "daily_${getTodayDate()}.json")
    private val stepLog = mutableListOf<StepMetrics>()
    private val errorLog = mutableListOf<Pair<ErrorCategory, String>>()
    private val fallbackLog = mutableListOf<Triple<String, Int, Boolean>>()
    
    init {
        metricsDir.mkdirs()
    }
    
    fun logStepStart(stepId: String, action: String) {
        // Track step start for duration calculation
    }
    
    fun logStepComplete(stepId: String, status: String, durationMs: Long) {
        stepLog.add(StepMetrics(stepId, "", status, durationMs, 0, System.currentTimeMillis()))
    }
    
    fun logError(category: ErrorCategory, message: String) {
        errorLog.add(Pair(category, message))
    }
    
    fun logFallbackSuccess(method: String, attempts: Int) {
        fallbackLog.add(Triple(method, attempts, true))
    }
    
    fun logFallbackFailure(method: String, error: String) {
        fallbackLog.add(Triple(method, 0, false))
    }
    
    fun getTaskSummary(): TaskMetrics? {
        if (stepLog.isEmpty()) return null
        val completed = stepLog.count { it.status == "SUCCESS" }
        val failed = stepLog.count { it.status == "FAILED" }
        val totalDuration = stepLog.sumOf { it.durationMs }
        return TaskMetrics("", "", stepLog.size, completed, failed, totalDuration, 0, failed == 0)
    }
    
    fun getErrorBreakdown(): Map<String, Int> {
        return errorLog.groupBy { it.first.name }.mapValues { it.value.size }
    }
    
    fun getSuccessRate(): Float {
        if (stepLog.isEmpty()) return 1f
        val success = stepLog.count { it.status == "SUCCESS" }
        return success.toFloat() / stepLog.size
    }
    
    fun getAverageStepDuration(): Long {
        if (stepLog.isEmpty()) return 0
        return stepLog.sumOf { it.durationMs } / stepLog.size
    }
    
    fun getFallbackSuccessRate(): Float {
        if (fallbackLog.isEmpty()) return 1f
        val success = fallbackLog.count { it.third }
        return success.toFloat() / fallbackLog.size
    }
    
    fun saveDailyReport() {
        val json = JSONObject().apply {
            put("date", getTodayDate())
            put("totalSteps", stepLog.size)
            put("successRate", getSuccessRate().toDouble())
            put("avgDuration", getAverageStepDuration())
            put("errors", JSONObject(getErrorBreakdown()))
            put("fallbackRate", getFallbackSuccessRate().toDouble())
        }
        dailyMetricsFile.writeText(json.toString())
    }
    
    fun getWeeklyReport(): String {
        val reports = metricsDir.listFiles()?.filter { it.name.startsWith("daily_") }?.sortedByDescending { it.lastModified() }?.take(7) ?: emptyList()
        
        if (reports.isEmpty()) return "No metrics data available"
        
        val sb = StringBuilder("📊 WEEKLY AURA METRICS:\n\n")
        var totalSteps = 0
        var totalSuccess = 0.0
        
        for (report in reports) {
            try {
                val json = JSONObject(report.readText())
                val steps = json.getInt("totalSteps")
                val success = json.getDouble("successRate")
                totalSteps += steps
                totalSuccess += success * steps
                sb.append("${json.getString("date")}: $steps steps, ${(success * 100).toInt()}% success\n")
            } catch (e: Exception) { }
        }
        
        val overallSuccess = if (totalSteps > 0) totalSuccess / totalSteps else 0.0
        sb.append("\n📈 Overall: ${(overallSuccess * 100).toInt()}% success rate")
        
        return sb.toString()
    }
    
    private fun getTodayDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    
    fun clearLogs() {
        stepLog.clear()
        errorLog.clear()
        fallbackLog.clear()
    }
}
