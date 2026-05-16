package com.aura.ai.utils.helpers

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RateLimiter(
    private val maxRequests: Int,
    private val timeWindowMs: Long
) {
    
    private val mutex = Mutex()
    private val requestCount = AtomicInteger(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())
    
    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            
            if (now - windowStart.get() > timeWindowMs) {
                requestCount.set(0)
                windowStart.set(now)
            }
            
            if (requestCount.get() >= maxRequests) {
                val waitTime = timeWindowMs - (now - windowStart.get())
                delay(waitTime)
                requestCount.set(0)
                windowStart.set(System.currentTimeMillis())
            }
            
            requestCount.incrementAndGet()
        }
    }
}
