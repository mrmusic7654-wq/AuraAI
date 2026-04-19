package com.aura.ai.di

import com.aura.ai.data.remote.api.ApiConstants
import com.aura.ai.data.remote.api.GeminiApi
import com.aura.ai.data.remote.api.GitHubApi
import com.aura.ai.data.remote.interceptors.AuthInterceptor
import com.aura.ai.data.remote.interceptors.GitHubAuthInterceptor
import com.aura.ai.data.remote.interceptors.LoggingInterceptor
import com.aura.ai.data.remote.interceptors.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }
    
    @Provides
    @Singleton
    fun provideJson(): Json = json
    
    @Provides
    @Singleton
    @Named("gemini_client")
    fun provideGeminiOkHttpClient(
        authInterceptor: AuthInterceptor,
        rateLimitInterceptor: RateLimitInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(ApiConstants.TIMEOUT_CONNECT, TimeUnit.SECONDS)
            .readTimeout(ApiConstants.TIMEOUT_READ, TimeUnit.SECONDS)
            .writeTimeout(ApiConstants.TIMEOUT_WRITE, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    @Provides
    @Singleton
    @Named("github_client")
    fun provideGitHubOkHttpClient(
        gitHubAuthInterceptor: GitHubAuthInterceptor,
        rateLimitInterceptor: RateLimitInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(gitHubAuthInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(ApiConstants.TIMEOUT_CONNECT, TimeUnit.SECONDS)
            .readTimeout(ApiConstants.TIMEOUT_READ, TimeUnit.SECONDS)
            .writeTimeout(ApiConstants.TIMEOUT_WRITE, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGeminiApi(
        @Named("gemini_client") client: OkHttpClient,
        json: Json
    ): GeminiApi {
        return Retrofit.Builder()
            .baseUrl(ApiConstants.GEMINI_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideGitHubApi(
        @Named("github_client") client: OkHttpClient,
        json: Json
    ): GitHubApi {
        return Retrofit.Builder()
            .baseUrl(ApiConstants.GITHUB_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}
