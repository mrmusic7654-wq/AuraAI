package com.aura.ai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aura.ai.MainActivity
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_TASKS, "Task Updates", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
            ))
        }
    }

    fun showTaskCompleted(description: String, taskId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setContentTitle("Task Completed")
            .setContentText(description)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(taskId.hashCode(), notification)
    }

    fun showTaskFailed(description: String, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Task Failed")
            .setContentText("$description: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showAutomationTriggered(ruleName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setContentTitle("Automation Triggered")
            .setContentText("Rule: $ruleName")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ruleName.hashCode(), notification)
    }
}
