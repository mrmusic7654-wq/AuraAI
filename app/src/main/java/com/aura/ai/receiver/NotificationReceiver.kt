package com.aura.ai.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aura.ai.R
import com.aura.ai.services.AuraForegroundService

class NotificationReceiver : BroadcastReceiver() {
    
    companion object {
        const val CHANNEL_ID = "aura_notifications"
        const val NOTIFICATION_ID = 2001
        const val ACTION_HEARTBEAT = "com.aura.ai.HEARTBEAT"
        const val ACTION_TASK_COMPLETE = "com.aura.ai.TASK_COMPLETE"
        const val ACTION_ERROR = "com.aura.ai.ERROR"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AuraForegroundService.ACTION_STOP -> {
                AuraForegroundService.stop(context)
            }
            ACTION_HEARTBEAT -> {
                showHeartbeatNotification(context)
            }
            ACTION_TASK_COMPLETE -> {
                showTaskCompleteNotification(context, intent.getStringExtra("task_name") ?: "Task")
            }
            ACTION_ERROR -> {
                showErrorNotification(context, intent.getStringExtra("error_message") ?: "Unknown error")
            }
        }
    }
    
    private fun showHeartbeatNotification(context: Context) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🟢 Aura is Active")
            .setContentText("Autonomous mode running")
            .setSmallIcon(R.drawable.ic_aura_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showTaskCompleteNotification(context: Context, taskName: String) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("✅ Task Complete")
            .setContentText(taskName)
            .setSmallIcon(R.drawable.ic_aura_logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun showErrorNotification(context: Context, errorMessage: String) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("❌ Error Detected")
            .setContentText(errorMessage.take(100))
            .setSmallIcon(R.drawable.ic_aura_logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 2, notification)
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from Aura AI"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
