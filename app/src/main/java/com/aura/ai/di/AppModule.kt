package com.aura.ai.di

import android.content.Context
import androidx.room.Room
import com.aura.ai.data.local.database.AuraDatabase
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.repository.AgentRepositoryImpl
import com.aura.ai.data.repository.AutomationRepositoryImpl
import com.aura.ai.data.repository.TaskRepositoryImpl
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.AutomationRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideAuraPreferences(@ApplicationContext context: Context): AuraPreferences = AuraPreferences(context)

    @Provides @Singleton
    fun provideAuraDatabase(@ApplicationContext context: Context): AuraDatabase =
        Room.databaseBuilder(context, AuraDatabase::class.java, "aura_database").fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun provideGenerativeModel(auraPreferences: AuraPreferences): GenerativeModel {
        val config = generationConfig { temperature = 0.7f; topK = 40; topP = 0.95f; maxOutputTokens = 2048 }
        return GenerativeModel(modelName = "gemini-2.0-flash-exp", apiKey = auraPreferences.getApiKey() ?: "", generationConfig = config)
    }

    @Provides @Singleton
    fun provideScreenStateManager(): ScreenStateManager = ScreenStateManager()

    @Provides @Singleton
    fun provideTaskRepository(database: AuraDatabase): TaskRepository = TaskRepositoryImpl(database.taskDao())

    @Provides @Singleton
    fun provideAgentRepository(generativeModel: GenerativeModel, taskRepository: TaskRepository, screenStateManager: ScreenStateManager, preferences: AuraPreferences): AgentRepository =
        AgentRepositoryImpl(generativeModel, taskRepository, screenStateManager, preferences)

    @Provides @Singleton
    fun provideAutomationRepository(ruleDao: com.aura.ai.data.local.dao.AutomationRuleDao): AutomationRepository = AutomationRepositoryImpl(ruleDao)
}
