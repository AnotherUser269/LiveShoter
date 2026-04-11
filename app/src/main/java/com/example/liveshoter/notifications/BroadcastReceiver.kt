package com.example.liveshoter.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationHelper.ACTION_CAPTURE -> {
                Log.d("NotifReceiver", "Capture pressed")
                // TODO: логика capture
            }
            NotificationHelper.ACTION_EXIT -> {
                Log.d("NotifReceiver", "Exit pressed — cancel notification")
                NotificationHelper.cancelNotification(context)
            }
        }
    }
}
