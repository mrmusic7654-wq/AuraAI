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
            "settings" to "com.android.settings"
        )
        
        fun resolve(name: String): String? {
            return SUPPORTED_APPS[name.lowercase()] ?: name
        }
    }
    
    // ═══════════════════════════════════════════
    // PUBLIC METHODS (called by other files)
    // ═══════════════════════════════════════════
    
    fun openApp(packageName: String) {
        val intent = service.context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        service.context.startActivity(intent)
    }
    
    suspend fun execute(app: String, steps: List<AppStep>): String {
        openApp(app)
        delay(2000)
        var r = ""
        for ((i, s) in steps.withIndex()) {
            try {
                when (s.action) {
                    "tap" -> tap(s.target)
                    "type" -> type(s.target)
                    "swipe" -> swipe(s.target == "up")
                    "read" -> r = read()
                    "wait" -> delay(s.waitMs)
                    "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
                delay(s.waitMs)
            } catch (e: Exception) { return "Step ${i+1} failed: ${e.message}" }
        }
        return r
    }
    
    suspend fun sendMessage(app: String, msg: String): String {
        return execute(app, listOf(
            AppStep("tap","Message"), AppStep("type",msg), AppStep("tap","Send"),
            AppStep("wait","",5000), AppStep("read")
        ))
    }
    
    suspend fun debug(app: String, error: String): String {
        return sendMessage(app, "Fix this build error:\n$error")
    }
    
    suspend fun waitForResponse(timeout: Int = 120): String {
        var last = ""
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout * 1000L) {
            delay(3000)
            val cur = read()
            if (cur == last && cur.isNotEmpty()) { delay(2000); return read() }
            if (cur.contains("Copy") || cur.contains("Regenerate")) return cur
            last = cur
        }
        return read()
    }
    
    suspend fun analyzeScreen(apiKey: String, prompt: String): String {
        return try {
            val model = GenerativeModel("gemini-2.5-flash", apiKey, generationConfig { maxOutputTokens = 60000 })
            model.generateContent(content { text("Screen text: ${read().take(3000)}\n\n$prompt") }).text ?: "No response"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
    
    fun getCurrentApp(): String {
        val root = service.rootInActiveWindow ?: return "unknown"
        val pkg = root.packageName?.toString() ?: "unknown"
        root.recycle()
        return pkg
    }
    
    fun isAppOpen(pkg: String): Boolean = getCurrentApp() == pkg
    
    suspend fun waitForApp(pkg: String, timeout: Int = 10): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout * 1000L) {
            if (isAppOpen(pkg)) return true
            delay(1000)
        }
        return false
    }
    
    fun read(): String {
        val root = service.rootInActiveWindow ?: return ""
        val t = collect(root)
        root.recycle()
        return t
    }
    
    // ═══════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════
    
    private fun tap(text: String) {
        val root = service.rootInActiveWindow ?: return
        findNode(root, text)?.let {
            val r = Rect(); it.getBoundsInScreen(r); it.recycle()
            tapAt(r.centerX().toFloat(), r.centerY().toFloat())
        }
        root.recycle()
    }
    
    private fun type(text: String) {
        val root = service.rootInActiveWindow ?: return
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let {
            val a = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
            it.recycle()
        }
        root.recycle()
    }
    
    private fun swipe(up: Boolean) {
        val d = service.resources.displayMetrics
        val s = if (up) d.heightPixels*0.8f else d.heightPixels*0.2f
        val e = if (up) d.heightPixels*0.2f else d.heightPixels*0.8f
        val p = android.graphics.Path().apply { moveTo(d.widthPixels/2f, s); lineTo(d.widthPixels/2f, e) }
        service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 300))
                .build(), null, null
        )
    }
    
    private fun tapAt(x: Float, y: Float) {
        val p = android.graphics.Path().apply { moveTo(x, y) }
        service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 1))
                .build(), null, null
        )
    }
    
    private fun collect(n: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (n.text?.isNotBlank() == true && n.text.length > 3) sb.appendLine(n.text)
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { sb.append(collect(it)); it.recycle() }
        }
        return sb.toString()
    }
    
    private fun findNode(n: AccessibilityNodeInfo, t: String): AccessibilityNodeInfo? {
        if (n.text?.contains(t, true) == true || n.contentDescription?.contains(t, true) == true) return n
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { findNode(it, t)?.let { return it } }
        }
        return null
    }
    
    private fun takeScreenshot(): Bitmap? {
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            null
        } catch (e: Exception) { null }
    }
}
