package com.aura.ai.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aura.ai.R
import com.aura.ai.services.AuraForegroundService

class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_HEARTBEAT = "com.aura.ai.HEARTBEAT"
        const val ACTION_TASK_DONE = "com.aura.ai.TASK_DONE"
        const val ACTION_ERROR = "com.aura.ai.ERROR"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AuraForegroundService.ACTION_STOP -> {
                AuraForegroundService.stop(context)
            }
            ACTION_HEARTBEAT -> {
                showNotification(context, "🟢 Aura Active", "Autonomous mode running")
            }
            ACTION_TASK_DONE -> {
                showNotification(context, "✅ Task Complete", intent.getStringExtra("msg") ?: "Done")
            }
            ACTION_ERROR -> {
                showNotification(context, "❌ Error", intent.getStringExtra("msg") ?: "Unknown error")
            }
        }
    }
    
    private fun showNotification(context: Context, title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AuraForegroundService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_aura_logo)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
