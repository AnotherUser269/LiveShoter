package com.example.liveshoter.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class NotificationActionService: Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationHelper.ACTION_CAPTURE -> {
                // TODO: логика capture
                Log.d("NotificationAction", "Capture pressed")
            }
            NotificationHelper.ACTION_EXIT -> {
                NotificationHelper.cancelNotification(this)
            }
        }
        // останавливаем сервис после обработки
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
