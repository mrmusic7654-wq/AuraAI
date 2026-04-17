package com.aura.ai.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.dao.TaskDao
import com.aura.ai.data.local.entities.AutomationRuleEntity
import com.aura.ai.data.local.entities.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        AutomationRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun automationRuleDao(): AutomationRuleDao
}
