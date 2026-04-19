package com.aura.ai.di

import android.content.Context
import androidx.room.Room
import com.aura.ai.data.local.database.AuraDatabase
import com.aura.ai.data.local.database.Converters
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
    fun provideAuraDatabase(
        @ApplicationContext context: Context
    ): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_database"
        )
        .addTypeConverter(Converters())
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    @Singleton
    fun provideTaskDao(database: AuraDatabase) = database.taskDao()
    
    @Provides
    @Singleton
    fun provideAutomationRuleDao(database: AuraDatabase) = database.automationRuleDao()
    
    @Provides
    @Singleton
    fun provideAppUsageDao(database: AuraDatabase) = database.appUsageDao()
    
    @Provides
    @Singleton
    fun provideGitHubRepoDao(database: AuraDatabase) = database.gitHubRepoDao()
}
