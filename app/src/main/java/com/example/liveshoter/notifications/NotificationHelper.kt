package com.example.liveshoter.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

/**
 * Управляет уведомлениями приложения.
 *
 * Создаёт два канала:
 * - [CHANNEL_ID] — для управляющего уведомления с кнопками «Capture» и «Exit»;
 * - [OVERLAY_CHANNEL_ID] — для временного уведомления во время работы оверлея.
 */
object NotificationHelper {
    // Управляющее уведомление (кнопки Capture/Exit)
    const val CHANNEL_ID = "capture_channel"
    const val CHANNEL_NAME = "Capture Service"
    const val NOTIF_ID = 1001
    const val ACTION_CAPTURE = "com.example.liveshoter.notifications.ACTION_CAPTURE"
    const val ACTION_EXIT = "com.example.liveshoter.notifications.ACTION_EXIT"

    // Временное уведомление оверлея
    const val OVERLAY_CHANNEL_ID = "overlay_channel"
    const val OVERLAY_NOTIF_ID = 9911

    /**
     * Создаёт каналы уведомлений. Безопасно вызывать несколько раз.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val actionChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Capture control notification"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(actionChannel)

        val overlayChannel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "Overlay Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when overlay is active"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        nm.createNotificationChannel(overlayChannel)
    }

    /**
     * Строит управляющее уведомление с кнопками «Capture» и «Exit».
     */
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

    /**
     * Строит тихое уведомление, которое показывается на время работы оверлея.
     */
    fun buildForegroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, OVERLAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("LiveShoter")
            .setContentText("Preparing capture...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * Показывает управляющее уведомление (с кнопками).
     */
    fun showActionNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildActionNotification(context))
    }

    /**
     * Убирает управляющее уведомление.
     */
    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}