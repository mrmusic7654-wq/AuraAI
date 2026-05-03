package com.aura.ai.di

import android.content.Context
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
    fun providePreferences(@ApplicationContext c: Context) = AuraPreferences(c)

    @Provides @Singleton
    fun provideGenerativeModel(p: AuraPreferences) = GenerativeModel("gemini-2.0-flash-exp", p.getApiKey() ?: "", generationConfig { temperature = 0.7f })

    @Provides @Singleton
    fun provideScreenStateManager() = ScreenStateManager()

    @Provides @Singleton
    fun provideTaskRepository(db: AuraDatabase, prefs: AuraPreferences): TaskRepository = TaskRepositoryImpl(db.taskDao(), prefs)

    @Provides @Singleton
    fun provideAgentRepository(gm: GenerativeModel, tr: TaskRepository, sm: ScreenStateManager, p: AuraPreferences): AgentRepository = AgentRepositoryImpl(gm, tr, sm, p)

    @Provides @Singleton
    fun provideAutomationRepository(d: com.aura.ai.data.local.dao.AutomationRuleDao): AutomationRepository = AutomationRepositoryImpl(d)
}
