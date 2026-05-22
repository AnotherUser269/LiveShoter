package com.example.liveshoter.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "capture_channel"
    private const val CHANNEL_NAME = "Capture Service"
    const val NOTIF_ID = 1001
    const val ACTION_CAPTURE = "com.example.liveshoter.notifications.ACTION_CAPTURE"
    const val ACTION_EXIT = "com.example.liveshoter.notifications.ACTION_EXIT"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Capture control notification"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun buildActionNotification(context: Context): Notification {
        val captureIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_CAPTURE
        }
        val exitIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_EXIT
        }

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
            .setContentTitle("LiveShoter")
            .setContentText("Tap Capture or Exit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_camera, "Capture", capturePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPending)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    fun showActionNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildActionNotification(context))
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}