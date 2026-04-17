package com.aura.ai.data.remote.interceptors

import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {
    
    private val requestCount = AtomicInteger(0)
    private val lastResetTime = AtomicLong(System.currentTimeMillis())
    private val maxRequestsPerMinute = 14 // Below 15 RPM limit
    private val retryDelayMs = 4000L
    
    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(this) {
            val now = System.currentTimeMillis()
            
            if (now - lastResetTime.get() > 60000) {
                requestCount.set(0)
                lastResetTime.set(now)
            }
            
            if (requestCount.get() >= maxRequestsPerMinute) {
                Timber.w("Rate limit approaching, waiting...")
                Thread.sleep(2000)
            }
            
            requestCount.incrementAndGet()
        }
        
        var response = chain.proceed(chain.request())
        
        if (response.code == 429) {
            response.close()
            Timber.w("Rate limit hit (429), waiting and retrying...")
            Thread.sleep(retryDelayMs)
            response = chain.proceed(chain.request())
        }
        
        return response
    }
}
