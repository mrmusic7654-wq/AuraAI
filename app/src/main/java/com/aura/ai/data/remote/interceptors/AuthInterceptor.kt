package com.aura.ai.data.remote.interceptors

import com.aura.ai.data.local.preferences.AuraPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val preferences: AuraPreferences
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val apiKey = preferences.getApiKey()
        
        return if (!apiKey.isNullOrBlank()) {
            val request = originalRequest.newBuilder()
                .header("x-goog-api-key", apiKey)
                .build()
            chain.proceed(request)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
