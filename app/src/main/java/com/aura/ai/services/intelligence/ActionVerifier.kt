package com.aura.ai.services.intelligence

import android.graphics.Bitmap
import com.aura.ai.services.AppController
import kotlinx.coroutines.delay

class ActionVerifier(private val appController: AppController) {
    
    private var lastScreenshot: Bitmap? = null
    private var lastScreenText: String = ""
    
    suspend fun verifyAppOpened(packageName: String): Boolean {
        var attempts = 0
        repeat(5) {
            if (appController.isAppOpen(packageName)) {
                lastScreenText = appController.execute(packageName, listOf(AppController.AppStep("read")))
                return true
            }
            delay(500)
            attempts++
        }
        return false
    }
    
    suspend fun verifyTap(target: String): Boolean {
        val beforeText = lastScreenText
        delay(500)
        val afterText = appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
        
        val changed = beforeText != afterText
        if (changed) lastScreenText = afterText
        
        return when {
            target.contains("coordinates") -> true
            afterText.contains(target, ignoreCase = true) -> true
            changed -> true
            else -> false
        }
    }
    
    suspend fun verifyTyped(text: String): Boolean {
        delay(300)
        val screenText = appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
        val found = screenText.contains(text.take(10), ignoreCase = true) || screenText.contains(text.takeLast(10), ignoreCase = true)
        if (found) lastScreenText = screenText
        return found
    }
    
    suspend fun verifyScreenChange(): Boolean {
        val before = lastScreenText
        delay(1000)
        val after = appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
        val changed = before != after
        if (changed) lastScreenText = after
        return changed
    }
    
    fun getLastScreenText(): String = lastScreenText
    
    fun getConfidenceScore(): Float {
        return if (lastScreenshot != null && lastScreenText.isNotEmpty()) 0.9f
        else if (lastScreenText.isNotEmpty()) 0.7f
        else 0.3f
    }
}
