package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.ActionType
import com.aura.ai.data.models.ScreenContext
import com.aura.ai.data.remote.datasource.GeminiRemoteDataSource
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class PlanActionsUseCase @Inject constructor(
    private val geminiDataSource: GeminiRemoteDataSource
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend operator fun invoke(
        taskDescription: String,
        screenContext: ScreenContext?,
        parsedIntent: ParseResult
    ): List<AgentAction> {
        return try {
            val screenInfo = screenContext?.toTextRepresentation() ?: "Screen context not available"
            
            val prompt = """
                You are a phone automation agent. Based on the user's request and current screen state,
                plan the exact sequence of actions needed.
                
                User Request: $taskDescription
                Parsed Intent: $parsedIntent
                
                Current Screen:
                $screenInfo
                
                Available Actions:
                - TAP: { "type": "TAP", "target": "element text", "x": null, "y": null }
                - TAP_COORD: { "type": "TAP", "target": null, "x": 500, "y": 1000 }
                - SWIPE: { "type": "SWIPE", "startX": 500, "startY": 1500, "endX": 500, "endY": 500, "duration": 300 }
                - TYPE: { "type": "TYPE", "text": "text to type" }
                - BACK: { "type": "BACK" }
                - HOME: { "type": "HOME" }
                - WAIT: { "type": "WAIT", "duration": 1000 }
                - OPEN_APP: { "type": "OPEN_APP", "packageName": "com.example.app" }
                
                Return a JSON array of actions. Example:
                [
                    { "type": "HOME" },
                    { "type": "WAIT", "duration": 500 },
                    { "type": "OPEN_APP", "packageName": "com.whatsapp" }
                ]
                
                Actions:
            """.trimIndent()
            
            val response = geminiDataSource.generateResponse(prompt)
            
            response.fold(
                onSuccess = { text ->
                    parseActionsFromJson(text)
                },
                onFailure = {
                    getFallbackActions(parsedIntent)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to plan actions")
            getFallbackActions(parsedIntent)
        }
    }
    
    private fun parseActionsFromJson(jsonText: String): List<AgentAction> {
        return try {
            val cleanJson = jsonText.substringAfter("[").substringBeforeLast("]")
            val jsonArray = "[$cleanJson]"
            json.decodeFromString<List<AgentActionDto>>(jsonArray).map { it.toAgentAction() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse actions JSON")
            emptyList()
        }
    }
    
    private fun getFallbackActions(parsedIntent: ParseResult): List<AgentAction> {
        val actions = mutableListOf<AgentAction>()
        
        when (parsedIntent) {
            is ParseResult.Success -> {
                parsedIntent.targetApp?.let { app ->
                    val packageName = getPackageName(app)
                    actions.add(AgentAction(type = ActionType.HOME))
                    actions.add(AgentAction(type = ActionType.WAIT, duration = 500))
                    actions.add(AgentAction(type = ActionType.OPEN_APP, packageName = packageName))
                }
            }
            else -> {
                actions.add(AgentAction(type = ActionType.HOME))
            }
        }
        
        return actions
    }
    
    private fun getPackageName(appName: String): String {
        return when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "instagram" -> "com.instagram.android"
            "facebook" -> "com.facebook.katana"
            "twitter", "x" -> "com.twitter.android"
            "youtube" -> "com.google.android.youtube"
            "gmail" -> "com.google.android.gm"
            "chrome" -> "com.android.chrome"
            "settings" -> "com.android.settings"
            else -> appName
        }
    }
}

@kotlinx.serialization.Serializable
data class AgentActionDto(
    val type: String,
    val target: String? = null,
    val text: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val duration: Long = 300,
    val packageName: String? = null
) {
    fun toAgentAction(): AgentAction {
        return AgentAction(
            type = ActionType.valueOf(type),
            target = target,
            text = text,
            x = x,
            y = y,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = duration,
            packageName = packageName
        )
    }
}
