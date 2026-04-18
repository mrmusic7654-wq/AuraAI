package com.aura.ai.data.remote.interceptors

import com.aura.ai.data.local.preferences.AuraPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class GitHubAuthInterceptor @Inject constructor(
    private val preferences: AuraPreferences
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = preferences.getGitHubToken()
        
        return if (!token.isNullOrBlank()) {
            val request = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            chain.proceed(request)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
