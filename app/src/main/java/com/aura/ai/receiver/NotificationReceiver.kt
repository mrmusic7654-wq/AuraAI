package com.aura.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.ai.services.AuraForegroundService

class NotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AuraForegroundService.ACTION_STOP -> {
                val serviceIntent = Intent(context, AuraForegroundService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
