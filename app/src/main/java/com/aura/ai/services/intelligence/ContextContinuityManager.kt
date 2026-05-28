package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class ContextSnapshot(
    val timestamp: Long,
    val currentApp: String,
    val screenTextPreview: String,
    val taskId: String,
    val stepIndex: Int,
    val overallStatus: String
)

class ContextContinuityManager(context: Context) {
    
    private val snapshotDir = File(context.filesDir, "context_snapshots")
    private val snapshotFile = File(snapshotDir, "latest_snapshot.json")
    private var snapshots = mutableListOf<ContextSnapshot>()
    
    init {
        snapshotDir.mkdirs()
        loadLatestSnapshot()
    }
    
    fun takeSnapshot(app: String, screenText: String, taskId: String, stepIndex: Int, status: String) {
        val snapshot = ContextSnapshot(
            timestamp = System.currentTimeMillis(),
            currentApp = app,
            screenTextPreview = screenText.take(200),
            taskId = taskId,
            stepIndex = stepIndex,
            overallStatus = status
        )
        snapshots.add(snapshot)
        if (snapshots.size > 50) snapshots.removeAt(0)
        saveSnapshot(snapshot)
    }
    
    fun getLatestSnapshot(): ContextSnapshot? {
        return snapshots.lastOrNull()
    }
    
    fun detectInterruption(currentApp: String, currentTask: String): Boolean {
        val last = snapshots.lastOrNull() ?: return false
        return last.currentApp != currentApp || last.taskId != currentTask
    }
    
    fun getResumeContext(): String {
        val last = snapshots.lastOrNull() ?: return "No previous context"
        return """
📋 RESUME CONTEXT:
App: ${last.currentApp}
Task: ${last.taskId}
Step: ${last.stepIndex}
Status: ${last.overallStatus}
Last seen: ${last.screenTextPreview}
Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(last.timestamp))}
        """.trimIndent()
    }
    
    fun wasInterrupted(): Boolean {
        if (snapshots.size < 2) return false
        val last = snapshots[snapshots.size - 1]
        val previous = snapshots[snapshots.size - 2]
        return last.currentApp != previous.currentApp || last.taskId != previous.taskId
    }
    
    fun getInterruptionReason(): String {
        if (!wasInterrupted()) return "No interruption detected"
        val last = snapshots.last()
        val previous = snapshots[snapshots.size - 2]
        return when {
            last.currentApp != previous.currentApp -> "App switched from ${previous.currentApp} to ${last.currentApp}"
            last.taskId != previous.taskId -> "Task changed"
            else -> "Unknown interruption"
        }
    }
    
    private fun saveSnapshot(snapshot: ContextSnapshot) {
        try {
            val json = JSONObject().apply {
                put("timestamp", snapshot.timestamp)
                put("currentApp", snapshot.currentApp)
                put("screenTextPreview", snapshot.screenTextPreview)
                put("taskId", snapshot.taskId)
                put("stepIndex", snapshot.stepIndex)
                put("overallStatus", snapshot.overallStatus)
            }
            snapshotFile.writeText(json.toString())
        } catch (e: Exception) { }
    }
    
    private fun loadLatestSnapshot() {
        if (!snapshotFile.exists()) return
        try {
            val json = JSONObject(snapshotFile.readText())
            snapshots.add(ContextSnapshot(
                timestamp = json.getLong("timestamp"),
                currentApp = json.getString("currentApp"),
                screenTextPreview = json.getString("screenTextPreview"),
                taskId = json.getString("taskId"),
                stepIndex = json.getInt("stepIndex"),
                overallStatus = json.getString("overallStatus")
            ))
        } catch (e: Exception) { }
    }
    
    fun clearHistory() {
        snapshots.clear()
        snapshotFile.delete()
    }
}
