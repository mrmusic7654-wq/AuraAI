package com.aura.ai.di

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.remote.interceptors.GitHubAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GitHubModule {
    
    @Provides
    @Singleton
    fun provideGitHubAuthInterceptor(
        preferences: AuraPreferences
    ): GitHubAuthInterceptor {
        return GitHubAuthInterceptor(preferences)
    }
}
