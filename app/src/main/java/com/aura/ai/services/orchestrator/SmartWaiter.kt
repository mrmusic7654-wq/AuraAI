package com.aura.ai.services.orchestrator

import com.aura.ai.services.AppController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class SmartWaiter(private val appController: AppController) {
    suspend fun waitForScreenChange(prev: String, timeoutMs: Long = 120000): String {
        return withTimeoutOrNull(timeoutMs) {
            var last = prev; var stable = 0
            while (true) {
                delay(3000); val cur = appController.read()
                if (cur != last) { stable = 0; last = cur }
                else if (cur.isNotEmpty()) { stable++; if (stable >= 3) return@withTimeoutOrNull cur }
                if (cur.contains("Copy")) return@withTimeoutOrNull cur
            }
        } ?: appController.read()
    }
    suspend fun waitForApp(pkg: String, timeoutMs: Long = 10000): Boolean {
        return withTimeoutOrNull(timeoutMs) { while (true) { if (appController.isAppOpen(pkg)) return@withTimeoutOrNull true; delay(500) } } ?: false
    }
}
