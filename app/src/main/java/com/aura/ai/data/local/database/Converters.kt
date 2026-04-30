package com.aura.ai.data.local.database

import androidx.room.TypeConverter
import com.aura.ai.data.models.ActionType
import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.TaskStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromActionType(type: ActionType): String = type.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)

    @TypeConverter
    fun fromActionsList(actions: List<AgentAction>): String = json.encodeToString(actions)

    @TypeConverter
    fun toActionsList(value: String): List<AgentAction> = json.decodeFromString(value)
}
