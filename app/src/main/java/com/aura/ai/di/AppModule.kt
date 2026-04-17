package com.aura.ai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.aura.ai.data.local.database.AuraDatabase
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.remote.api.GeminiApi
import com.aura.ai.data.remote.interceptors.AuthInterceptor
import com.aura.ai.data.remote.interceptors.RateLimitInterceptor
import com.aura.ai.data.repository.AgentRepositoryImpl
import com.aura.ai.data.repository.TaskRepositoryImpl
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.ScreenStateManager
import com.aura.ai.utils.constants.AppConstants
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "aura_preferences")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    @Provides
    @Singleton
    fun provideAuraPreferences(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>
    ): AuraPreferences {
        return AuraPreferences(context, dataStore)
    }
    
    @Provides
    @Singleton
    fun provideAuraDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_database"
        ).fallbackToDestructiveMigration()
         .build()
    }
    
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        rateLimitInterceptor: RateLimitInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGenerativeModel(
        auraPreferences: AuraPreferences
    ): GenerativeModel {
        val apiKey = auraPreferences.getApiKey() ?: ""
        
        val config = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 2048
        }
        
        return GenerativeModel(
            modelName = "gemini-2.0-flash-exp",
            apiKey = apiKey,
            generationConfig = config
        )
    }
    
    @Provides
    @Singleton
    fun provideScreenStateManager(): ScreenStateManager {
        return ScreenStateManager()
    }
    
    @Provides
    @Singleton
    fun provideTaskRepository(
        database: AuraDatabase,
        preferences: AuraPreferences
    ): TaskRepository {
        return TaskRepositoryImpl(database.taskDao(), preferences)
    }
    
    @Provides
    @Singleton
    fun provideAgentRepository(
        generativeModel: GenerativeModel,
        taskRepository: TaskRepository,
        screenStateManager: ScreenStateManager,
        preferences: AuraPreferences
    ): AgentRepository {
        return AgentRepositoryImpl(
            generativeModel,
            taskRepository,
            screenStateManager,
            preferences
        )
    }
}
