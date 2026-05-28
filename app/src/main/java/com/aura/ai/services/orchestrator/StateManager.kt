package com.aura.ai.services.orchestrator

import android.content.Context
import org.json.JSONObject
import java.io.File

class StateManager(context: Context) {
    
    private val stateDir = File(context.filesDir, "aura_state")
    private val stateFile = File(stateDir, "current_task.json")
    private val checkpointDir = File(stateDir, "checkpoints")
    
    init {
        stateDir.mkdirs()
        checkpointDir.mkdirs()
    }
    
    fun saveState(taskState: TaskState) {
        try {
            val json = JSONObject().apply {
                put("taskId", taskState.taskPlan.taskId)
                put("taskName", taskState.taskPlan.taskName)
                put("currentStepIndex", taskState.currentStepIndex)
                put("overallStatus", taskState.overallStatus)
                put("startedAt", taskState.startedAt)
                put("pausedAt", taskState.pausedAt)
                put("completedAt", taskState.completedAt)
                
                val stepsJson = JSONObject()
                taskState.stepResults.forEach { (id, result) ->
                    stepsJson.put(id, JSONObject().apply {
                        put("status", result.status)
                        put("output", result.output)
                        put("error", result.error)
                        put("durationMs", result.durationMs)
                        put("retriesUsed", result.retriesUsed)
                    })
                }
                put("stepResults", stepsJson)
            }
            
            stateFile.writeText(json.toString())
            saveCheckpoint(taskState)
        } catch (e: Exception) {
            // State save failure should not crash Aura
        }
    }
    
    fun getLastState(): TaskState? {
        if (!stateFile.exists()) return null
        return try {
            val json = JSONObject(stateFile.readText())
            parseState(json)
        } catch (e: Exception) { null }
    }
    
    fun clearState() {
        stateFile.delete()
        checkpointDir.listFiles()?.forEach { it.delete() }
    }
    
    fun saveCheckpoint(taskState: TaskState) {
        val checkpointFile = File(checkpointDir, "checkpoint_${taskState.currentStepIndex}.json")
        checkpointFile.writeText(stateFile.readText())
    }
    
    fun restoreFromCheckpoint(stepIndex: Int): TaskState? {
        val checkpointFile = File(checkpointDir, "checkpoint_$stepIndex.json")
        if (!checkpointFile.exists()) return null
        return try {
            parseState(JSONObject(checkpointFile.readText()))
        } catch (e: Exception) { null }
    }
    
    private fun parseState(json: JSONObject): TaskState {
        val stepsJson = json.getJSONObject("stepResults")
        val stepResults = mutableMapOf<String, StepResult>()
        stepsJson.keys().forEach { id ->
            val stepJson = stepsJson.getJSONObject(id)
            stepResults[id] = StepResult(
                stepId = id,
                status = stepJson.getString("status"),
                output = stepJson.optString("output", ""),
                error = stepJson.optString("error", ""),
                durationMs = stepJson.optLong("durationMs", 0),
                retriesUsed = stepJson.optInt("retriesUsed", 0)
            )
        }
        
        return TaskState(
            taskPlan = TaskPlan(
                taskId = json.getString("taskId"),
                taskName = json.getString("taskName"),
                steps = emptyList() // Steps are rebuilt from original plan
            ),
            currentStepIndex = json.getInt("currentStepIndex"),
            stepResults = stepResults,
            overallStatus = json.getString("overallStatus"),
            startedAt = json.optLong("startedAt", 0),
            pausedAt = json.optLong("pausedAt", 0),
            completedAt = json.optLong("completedAt", 0)
        )
    }
    
    fun getCheckpointCount(): Int = checkpointDir.listFiles()?.size ?: 0
    fun getStateSize(): Long = stateFile.length()
    fun hasUnfinishedTask(): Boolean = stateFile.exists() && getLastState()?.overallStatus == "RUNNING"
}
