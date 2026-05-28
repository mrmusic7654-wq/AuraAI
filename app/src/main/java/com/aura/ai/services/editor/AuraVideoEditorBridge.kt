package com.aura.ai.services.editor

import com.aura.ai.services.AppController
import kotlinx.coroutines.delay

class AuraVideoEditorBridge(
    private val appController: AppController
) {
    
    // ═══════════════════════════════════════════
    // HIGH-LEVEL COMMANDS AURA CAN CALL
    // ═══════════════════════════════════════════
    
    suspend fun addScene(videoUri: String): String {
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "ADD SCENE"),
            AppController.AppStep("wait", "", 2000)
        ))
        return "Scene added: $videoUri"
    }
    
    suspend fun addTextToScene(sceneIndex: Int, text: String, position: String): String {
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "Scene ${sceneIndex + 1}"),
            AppController.AppStep("tap", "TEXT"),
            AppController.AppStep("type", text),
            AppController.AppStep("tap", position.uppercase()),
            AppController.AppStep("tap", "Add Text")
        ))
        return "Text added to scene $sceneIndex"
    }
    
    suspend fun addTransition(sceneIndex: Int, transition: String): String {
        val transitionLabel = when (transition) {
            "fade" -> "Fade (Smooth)"
            "slide" -> "Slide Left"
            "zoom" -> "Zoom In"
            else -> "Cut (Instant)"
        }
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "Scene ${sceneIndex + 1}"),
            AppController.AppStep("tap", "TRANS"),
            AppController.AppStep("tap", transitionLabel),
            AppController.AppStep("tap", "Apply")
        ))
        return "Transition '$transition' added to scene $sceneIndex"
    }
    
    suspend fun reorderScene(fromIndex: Int, toIndex: Int): String {
        val scene = "Scene ${fromIndex + 1}"
        appController.execute("com.aura.ai", listOf(AppController.AppStep("tap", scene)))
        
        if (toIndex < fromIndex) {
            repeat(fromIndex - toIndex) {
                appController.execute("com.aura.ai", listOf(AppController.AppStep("tap", "Up")))
                delay(300)
            }
        } else {
            repeat(toIndex - fromIndex) {
                appController.execute("com.aura.ai", listOf(AppController.AppStep("tap", "Down")))
                delay(300)
            }
        }
        return "Scene moved from $fromIndex to $toIndex"
    }
    
    suspend fun deleteScene(sceneIndex: Int): String {
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "Scene ${sceneIndex + 1}"),
            AppController.AppStep("tap", "Delete")
        ))
        return "Scene $sceneIndex deleted"
    }
    
    suspend fun trimScene(sceneIndex: Int, startSeconds: Float, endSeconds: Float): String {
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "Scene ${sceneIndex + 1}"),
            AppController.AppStep("tap", "Trim"),
            AppController.AppStep("type", String.format("%.1f", startSeconds)),
            AppController.AppStep("tap", "End Time"),
            AppController.AppStep("type", String.format("%.1f", endSeconds)),
            AppController.AppStep("tap", "Apply Trim")
        ))
        return "Scene $sceneIndex trimmed: $startSeconds - $endSeconds"
    }
    
    suspend fun exportVideo(filename: String, quality: String): String {
        appController.execute("com.aura.ai", listOf(
            AppController.AppStep("tap", "EXPORT"),
            AppController.AppStep("type", filename),
            AppController.AppStep("tap", quality),
            AppController.AppStep("tap", "Start Export")
        ))
        return "Export started: $filename ($quality)"
    }
    
    suspend fun waitForExport(): String {
        var elapsed = 0
        while (elapsed < 600) { // 10 minutes max
            delay(5000)
            elapsed += 5
            val screenText = appController.execute("com.aura.ai", listOf(AppController.AppStep("read")))
            if (screenText.contains("Export complete") || screenText.contains("100%")) {
                return "Export completed after ${elapsed}s"
            }
        }
        return "Export timeout"
    }
}
