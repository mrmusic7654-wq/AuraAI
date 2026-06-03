package com.aura.ai.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.*

class AppController(private val service: AccessibilityService) {
    
    data class AppStep(
        val action: String,
        val target: String = "",
        val waitMs: Long = 1000
    )
    
    companion object {
        val SUPPORTED_APPS = mapOf(
            "deepseek" to "com.deepseek.chat",
            "chatgpt" to "com.openai.chatgpt",
            "gemini" to "com.google.android.apps.bard",
            "claude" to "com.anthropic.claude",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "settings" to "com.android.settings",
            "calculator" to "com.android.calculator2",
            "calendar" to "com.android.calendar",
            "files" to "com.android.documentsui"
        )
        
        fun resolveApp(name: String): String? {
            return SUPPORTED_APPS[name.lowercase()] ?: name
        }
    }
    
    // ═══════════════════════════════════════════
    // HIGH-LEVEL OPERATIONS
    // ═══════════════════════════════════════════
    
    suspend fun executeActions(appPackage: String, steps: List<AppStep>): String {
        openApp(appPackage)
        delay(2000)
        
        var result = ""
        for ((index, step) in steps.withIndex()) {
            try {
                when (step.action) {
                    "tap" -> tapOnText(step.target)
                    "type" -> typeText(step.target)
                    "swipe" -> performSwipe(step.target == "up")
                    "scroll" -> performSwipe(step.target == "up")
                    "read" -> result = readScreen()
                    "wait" -> delay(step.waitMs)
                    "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    "notifications" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                }
                delay(step.waitMs)
            } catch (e: Exception) {
                return "Step ${index + 1} failed: ${e.message}"
            }
        }
        
        return result
    }
    
    suspend fun sendMessageToApp(appPackage: String, message: String): String {
        return executeActions(appPackage, listOf(
            AppStep("tap", "Message"),
            AppStep("type", message),
            AppStep("tap", "Send"),
            AppStep("wait", "", 5000),
            AppStep("read")
        ))
    }
    
    suspend fun debugWithApp(appPackage: String, errorLogs: String): String {
        val prompt = """Fix this Android build error. Return ONLY the corrected code in this format:
===FILE:exact/file/path.kt===
[complete corrected code]
===END===

BUILD ERROR:
$errorLogs""".trimIndent()
        
        return sendMessageToApp(appPackage, prompt)
    }
    
    // ═══════════════════════════════════════════
    // SCREEN ANALYSIS
    // ═══════════════════════════════════════════
    
    suspend fun analyzeScreenWithGemini(apiKey: String, prompt: String): String {
        val bitmap = captureScreenshot()
        if (bitmap == null) {
            // Fallback to text reading
            val screenText = readScreen()
            return "📱 Screen text (screenshot unavailable):\n${screenText.take(2000)}"
        }
        
        val model = GenerativeModel(
            "gemini-2.5-flash",
            apiKey,
            generationConfig { maxOutputTokens = 60000 }
        )
        
        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            ).text ?: "No response from Gemini"
            response
        } catch (e: Exception) {
            "Screen analysis error: ${e.message}"
        }
    }
    
    private fun captureScreenshot(): Bitmap? {
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            null // Screenshot saved to gallery, not returned as bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    // ═══════════════════════════════════════════
    // SMART WAITING
    // ═══════════════════════════════════════════
    
    suspend fun waitForResponse(timeoutSeconds: Int = 120): String {
        var lastText = ""
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            delay(3000)
            val currentText = readScreen()
            
            // Check if response stabilized
            if (currentText == lastText && currentText.isNotEmpty()) {
                delay(2000)
                return readScreen()
            }
            
            // Check for completion indicators
            if (currentText.contains("Copy") || 
                currentText.contains("Regenerate") ||
                currentText.contains("👍") ||
                currentText.contains("Thumbs up")) {
                return currentText
            }
            
            lastText = currentText
        }
        
        return readScreen()
    }
    
    suspend fun waitForAppToOpen(packageName: String, timeoutSeconds: Int = 10): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000L) {
            if (isAppOpen(packageName)) return true
            delay(1000)
        }
        return false
    }
    
    fun isAppOpen(packageName: String): Boolean {
        return getCurrentAppPackage() == packageName
    }
    
    fun getCurrentAppPackage(): String {
        val root = service.rootInActiveWindow ?: return "unknown"
        val pkg = root.packageName?.toString() ?: "unknown"
        root.recycle()
        return pkg
    }
    
    // ═══════════════════════════════════════════
    // CORE ACTIONS
    // ═══════════════════════════════════════════
    
    fun openApp(packageName: String) {
        val intent = service.context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        service.context.startActivity(intent)
    }
    
    private fun tapOnText(text: String) {
        val root = service.rootInActiveWindow ?: return
        val node = findNodeByText(root, text)
        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            node.recycle()
            performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        root.recycle()
    }
    
    private fun typeText(text: String) {
        val root = service.rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
        root.recycle()
    }
    
    private fun performTap(x: Float, y: Float) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
    
    private fun performSwipe(up: Boolean) {
        val display = service.resources.displayMetrics
        val startY = if (up) display.heightPixels * 0.8f else display.heightPixels * 0.2f
        val endY = if (up) display.heightPixels * 0.2f else display.heightPixels * 0.8f
        val x = display.widthPixels / 2f
        
        val path = android.graphics.Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
    
    private fun readScreen(): String {
        val root = service.rootInActiveWindow ?: return ""
        val text = collectAllText(root)
        root.recycle()
        return text
    }
    
    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text?.isNotBlank() == true && node.text.length > 3) {
            sb.appendLine(node.text)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                sb.append(collectAllText(child))
                child.recycle()
            }
        }
        return sb.toString()
    }
    
    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.contains(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByText(child, text)?.let { return it }
            }
        }
        return null
    }
}
