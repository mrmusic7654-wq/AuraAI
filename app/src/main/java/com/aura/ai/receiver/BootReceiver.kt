package com.aura.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.ai.services.AuraForegroundService

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start foreground service on boot
            val serviceIntent = Intent(context, AuraForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
