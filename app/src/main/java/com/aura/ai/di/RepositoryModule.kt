package com.aura.ai.di

import com.aura.ai.data.local.dao.AppUsageDao
import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.dao.GitHubRepoDao
import com.aura.ai.data.local.dao.TaskDao
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.remote.api.GeminiApi
import com.aura.ai.data.remote.api.GitHubApi
import com.aura.ai.data.remote.datasource.GeminiRemoteDataSource
import com.aura.ai.data.remote.datasource.GitHubRemoteDataSource
import com.aura.ai.data.repository.*
import com.aura.ai.domain.repository.*
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideGeminiRemoteDataSource(
        geminiApi: GeminiApi
    ): GeminiRemoteDataSource {
        return GeminiRemoteDataSource(geminiApi)
    }
    
    @Provides
    @Singleton
    fun provideGitHubRemoteDataSource(
        gitHubApi: GitHubApi
    ): GitHubRemoteDataSource {
        return GitHubRemoteDataSource(gitHubApi)
    }
    
    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        preferences: AuraPreferences
    ): TaskRepository {
        return TaskRepositoryImpl(taskDao, preferences)
    }
    
    @Provides
    @Singleton
    fun provideAgentRepository(
        generativeModel: GenerativeModel,
        taskRepository: TaskRepository,
        screenStateManager: ScreenStateManager,
        preferences: AuraPreferences,
        geminiDataSource: GeminiRemoteDataSource
    ): AgentRepository {
        return AgentRepositoryImpl(
            generativeModel,
            taskRepository,
            screenStateManager,
            preferences,
            geminiDataSource
        )
    }
    
    @Provides
    @Singleton
    fun provideAutomationRepository(
        automationRuleDao: AutomationRuleDao
    ): AutomationRepository {
        return AutomationRepositoryImpl(automationRuleDao)
    }
    
    @Provides
    @Singleton
    fun provideScreenStateRepository(
        screenStateManager: ScreenStateManager
    ): ScreenStateRepository {
        return ScreenStateRepositoryImpl(screenStateManager)
    }
    
    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        appUsageDao: AppUsageDao
    ): AnalyticsRepository {
        return AnalyticsRepositoryImpl(appUsageDao)
    }
    
    @Provides
    @Singleton
    fun provideGitHubRepository(
        gitHubRemoteDataSource: GitHubRemoteDataSource,
        gitHubRepoDao: GitHubRepoDao,
        preferences: AuraPreferences
    ): GitHubRepository {
        return GitHubRepositoryImpl(gitHubRemoteDataSource, gitHubRepoDao, preferences)
    }
}
