package com.example.liveshoter.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.liveshoter.MainActivity
import com.example.liveshoter.capture.CaptureOverlayService
import com.example.liveshoter.capture.MediaProjectionService
import com.example.liveshoter.capture.ProjectionHolder

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationHelper.ACTION_CAPTURE -> handleCaptureAction(context)
            NotificationHelper.ACTION_EXIT -> handleExitAction(context)
        }
    }

    private fun handleCaptureAction(context: Context) {
        // Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        ) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(overlayIntent)
            return
        }

        // MediaProjection permission
        if (!ProjectionHolder.hasSavedPermission()) {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("request_projection", true)
            }
            context.startActivity(activityIntent)
            return
        }

        if (ProjectionHolder.hasProjection()) {
            context.startService(Intent(context, CaptureOverlayService::class.java))
            return
        }

        val holderIntent = Intent(context, MediaProjectionService::class.java).apply {
            putExtra(MediaProjectionService.EXTRA_START_CAPTURE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(holderIntent)
        } else {
            context.startService(holderIntent)
        }
    }

    private fun handleExitAction(context: Context) {
        context.stopService(Intent(context, CaptureOverlayService::class.java))
        context.stopService(Intent(context, MediaProjectionService::class.java))
        ProjectionHolder.clearPreferences(context)
        NotificationHelper.cancelNotification(context)
    }
}