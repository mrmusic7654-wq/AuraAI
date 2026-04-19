package com.aura.ai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aura.ai.MainActivity
import com.aura.ai.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val CHANNEL_TASKS = "aura_tasks"
        private const val CHANNEL_ALERTS = "aura_alerts"
    }
    
    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Task Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about task execution status"
            }
            
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Important Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts and errors"
            }
            
            notificationManager.createNotificationChannels(listOf(tasksChannel, alertsChannel))
        }
    }
    
    fun showTaskCompleted(taskDescription: String, taskId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setContentTitle("Task Completed")
            .setContentText(taskDescription)
            .setSmallIcon(R.drawable.ic_success)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(taskId.hashCode(), notification)
    }
    
    fun showTaskFailed(taskDescription: String, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Task Failed")
            .setContentText("$taskDescription: $error")
            .setSmallIcon(R.drawable.ic_error)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    fun showAutomationTriggered(ruleName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setContentTitle("Automation Triggered")
            .setContentText("Rule: $ruleName")
            .setSmallIcon(R.drawable.ic_automation)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(ruleName.hashCode(), notification)
    }
}
