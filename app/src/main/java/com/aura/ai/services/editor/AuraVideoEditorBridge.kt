package com.aura.ai.services.editor

import com.aura.ai.services.AppController

class AuraVideoEditorBridge(private val appController: AppController) {
    suspend fun tapButton(text: String): String { return "Tapped: $text" }
    suspend fun typeText(text: String): String { return "Typed: $text" }
    suspend fun waitForExport(timeoutSeconds: Int = 300): String { kotlinx.coroutines.delay((timeoutSeconds * 1000).toLong()); return "Export done" }
}
