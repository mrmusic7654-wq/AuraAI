package com.aura.ai.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aura.ai.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuraForegroundService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "aura_foreground"
        const val ACTION_STOP = "STOP_SERVICE"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura AI Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Aura AI running in the background"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AuraForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aura AI Active")
            .setContentText("Your AI assistant is running")
            .setSmallIcon(R.drawable.ic_aura_logo)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
