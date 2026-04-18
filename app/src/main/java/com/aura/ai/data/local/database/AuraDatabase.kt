package com.aura.ai.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aura.ai.data.local.dao.*
import com.aura.ai.data.local.entities.*

@Database(
    entities = [
        TaskEntity::class,
        AutomationRuleEntity::class,
        AppUsageEntity::class,
        ScreenElementEntity::class,
        GitHubRepoEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun screenElementDao(): ScreenElementDao
    abstract fun gitHubRepoDao(): GitHubRepoDao
}
