package com.aura.ai.services.intelligence

import com.aura.ai.services.AppController
import kotlinx.coroutines.delay

class ActionVerifier(private val appController: AppController) {
    private var lastText: String = ""
    
    suspend fun verifyAppOpened(pkg: String): Boolean {
        repeat(5) { if (appController.isAppOpen(pkg)) { lastText = appController.read(); return true }; delay(500) }
        return false
    }
    suspend fun verifyTap(target: String): Boolean {
        delay(500); val after = appController.read(); return after != lastText || after.contains(target, true)
    }
    suspend fun verifyTyped(text: String): Boolean {
        delay(300); return appController.read().contains(text.take(10), true)
    }
}
