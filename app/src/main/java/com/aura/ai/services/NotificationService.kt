fun showTaskPaused(taskDescription: String, taskId: String) {
    val resumeIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("RESUME_TASK_ID", taskId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val resumePendingIntent = PendingIntent.getActivity(
        context, 0, resumeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
        .setContentTitle("Task Paused")
        .setContentText("Tap to resume: $taskDescription")
        .setSmallIcon(R.drawable.ic_pause)
        .setContentIntent(resumePendingIntent)
        .addAction(
            0,
            "Resume",
            resumePendingIntent
        )
        .setAutoCancel(true)
        .setOngoing(true)
        .build()
    
    notificationManager.notify(taskId.hashCode(), notification)
}
