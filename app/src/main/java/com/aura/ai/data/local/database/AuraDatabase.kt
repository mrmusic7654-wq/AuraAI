package com.aura.ai.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aura.ai.data.local.dao.AppUsageDao
import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.dao.GitHubRepoDao
import com.aura.ai.data.local.dao.ScreenElementDao
import com.aura.ai.data.local.dao.TaskDao
import com.aura.ai.data.local.entities.AppUsageEntity
import com.aura.ai.data.local.entities.AutomationRuleEntity
import com.aura.ai.data.local.entities.GitHubRepoEntity
import com.aura.ai.data.local.entities.ScreenElementEntity
import com.aura.ai.data.local.entities.TaskEntity

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
