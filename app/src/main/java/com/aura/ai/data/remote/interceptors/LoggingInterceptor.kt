package com.aura.ai.data.remote.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject

class LoggingInterceptor @Inject constructor() : Interceptor {
    
    private val logger = HttpLoggingInterceptor { message ->
        if (BuildConfig.DEBUG) {
            android.util.Log.d("AuraAPI", message)
        }
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        return logger.intercept(chain)
    }
}
