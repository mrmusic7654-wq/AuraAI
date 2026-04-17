package com.aura.ai.data.local.database

import androidx.room.TypeConverter
import com.aura.ai.data.models.ActionType
import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.TaskStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    
    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toTaskStatus(status: String): TaskStatus {
        return TaskStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromActionType(type: ActionType): String {
        return type.name
    }
    
    @TypeConverter
    fun toActionType(type: String): ActionType {
        return ActionType.valueOf(type)
    }
    
    @TypeConverter
    fun fromActionsList(actions: List<AgentAction>): String {
        return Json.encodeToString(actions)
    }
    
    @TypeConverter
    fun toActionsList(actionsJson: String): List<AgentAction> {
        return Json.decodeFromString(actionsJson)
    }
}
