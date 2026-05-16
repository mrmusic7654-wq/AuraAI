package com.aura.ai.di

import android.content.Context
import androidx.room.Room
import com.aura.ai.data.local.database.AuraDatabase
import com.aura.ai.data.local.database.Converters
import com.aura.ai.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAuraDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(context, AuraDatabase::class.java, "aura_database")
            .addTypeConverter(Converters())
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides @Singleton
    fun provideTaskDao(db: AuraDatabase): TaskDao = db.taskDao()

    @Provides @Singleton
    fun provideAutomationRuleDao(db: AuraDatabase): AutomationRuleDao = db.automationRuleDao()

    @Provides @Singleton
    fun provideAppUsageDao(db: AuraDatabase): AppUsageDao = db.appUsageDao()

    @Provides @Singleton
    fun provideScreenElementDao(db: AuraDatabase): ScreenElementDao = db.screenElementDao()

    @Provides @Singleton
    fun provideGitHubRepoDao(db: AuraDatabase): GitHubRepoDao = db.gitHubRepoDao()

    @Provides @Singleton
    fun provideScheduledTaskDao(db: AuraDatabase): ScheduledTaskDao = db.scheduledTaskDao()

    @Provides @Singleton
    fun provideSwarmAgentDao(db: AuraDatabase): SwarmAgentDao = db.swarmAgentDao()
}
