package com.example.liveshoter.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.liveshoter.notifications.NotificationHelper

/**
 * Foreground‑сервис, постоянно удерживающий активный [MediaProjection].
 * Благодаря этому пользователь может выполнять захваты экрана без повторного
 * запроса разрешения. При остановке системой проекция автоматически
 * пересоздаётся (благодаря START_STICKY).
 *
 * Показывает тихое уведомление в отдельном канале, не пересекающееся
 * с управляющим уведомлением [NotificationHelper].
 */
class MediaProjectionService : Service() {

    companion object {
        const val CHANNEL_ID = "media_projection_holder"
        const val NOTIFICATION_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Загружаем сохранённые разрешения и при необходимости создаём проекцию
        ProjectionHolder.loadPermissionData(this)
        if (!ProjectionHolder.hasProjection() && ProjectionHolder.hasSavedPermission()) {
            createMediaProjection()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Если сервис был перезапущен системой, пробуем восстановить проекцию
        if (!ProjectionHolder.hasProjection() && ProjectionHolder.hasSavedPermission()) {
            createMediaProjection()
        }
        return START_STICKY
    }

    /**
     * Создаёт [MediaProjection] из ранее сохранённых [resultCode]/[resultData].
     * При успехе показывает управляющее уведомление, чтобы пользователь мог
     * начать захват.
     */
    private fun createMediaProjection() {
        val permData = ProjectionHolder.getSavedPermissionData() ?: return

        val (code, data) = permData
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val mp = manager.getMediaProjection(code, data)
            mp?.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    Log.w("MediaProjectionSvc", "MediaProjection stopped by system")
                    ProjectionHolder.mediaProjection = null
                }
            }, null)
            ProjectionHolder.mediaProjection = mp
            NotificationHelper.showActionNotification(this)
        } catch (e: Exception) {
            Log.e("MediaProjectionSvc", "Failed to create MediaProjection", e)
        }
    }

    override fun onDestroy() {
        ProjectionHolder.clear()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Projection Holder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps screen capture permission active"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("LiveShoter")
            .setContentText("Ready to capture")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}