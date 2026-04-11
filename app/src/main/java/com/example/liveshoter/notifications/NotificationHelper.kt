package com.example.liveshoter.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "capture_channel"
    const val CHANNEL_NAME = "Capture Channel"
    const val NOTIF_ID = 1001
    const val ACTION_CAPTURE = "com.example.liveshoter.notifications.ACTION_CAPTURE"
    const val ACTION_EXIT = "com.example.liveshoter.notifications.ACTION_EXIT"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // максимальный приоритет (для Android O+)
            ).apply {
                description = "Notifications for capture/exit"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context): Notification {
        val captureIntent = Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_CAPTURE }
        val exitIntent    = Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_EXIT }

        val capturePending = PendingIntent.getBroadcast(
            context, 0, captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exitPending = PendingIntent.getBroadcast(
            context, 1, exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Capture notification")
            .setContentText("Tap Capture or Exit")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_camera, "Capture", capturePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPending)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    fun showNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(context))
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
