package com.aura.ai.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aura.ai.R

class NotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "STOP_SERVICE" -> {
                val serviceIntent = Intent(context, com.aura.ai.services.AuraForegroundService::class.java)
                serviceIntent.action = "STOP_SERVICE"
                context.startService(serviceIntent)
            }
            "HEARTBEAT" -> {
                showNotification(context, "Aura Active", "Running")
            }
            "TASK_DONE" -> {
                showNotification(context, "Task Complete", intent.getStringExtra("msg") ?: "Done")
            }
            "ERROR" -> {
                showNotification(context, "Error", intent.getStringExtra("msg") ?: "Unknown")
            }
        }
    }
    
    private fun showNotification(context: Context, title: String, message: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, "aura_notifications")
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_aura_logo)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        } catch (e: Exception) { }
    }
}
