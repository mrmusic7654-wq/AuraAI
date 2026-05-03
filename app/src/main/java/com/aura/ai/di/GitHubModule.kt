package com.aura.ai.di

import com.aura.ai.data.local.dao.GitHubRepoDao
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.remote.datasource.GitHubRemoteDataSource
import com.aura.ai.data.repository.GitHubRepositoryImpl
import com.aura.ai.domain.repository.GitHubRepository
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
    fun provideGitHubRepository(
        dataSource: GitHubRemoteDataSource,
        repoDao: GitHubRepoDao,
        preferences: AuraPreferences
    ): GitHubRepository = GitHubRepositoryImpl(dataSource, repoDao, preferences)
}
