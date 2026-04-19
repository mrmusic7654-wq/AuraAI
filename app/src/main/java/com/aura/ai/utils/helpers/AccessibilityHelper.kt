package com.aura.ai.utils.helpers

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityHelper {
    
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    fun getServiceName(context: Context): String {
        return "${context.packageName}/com.aura.ai.services.AuraAccessibilityService"
    }
}
