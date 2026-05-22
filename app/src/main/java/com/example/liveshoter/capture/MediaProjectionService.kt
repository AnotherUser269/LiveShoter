package com.example.liveshoter.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import com.example.liveshoter.notifications.NotificationHelper

class MediaProjectionService : Service() {

    companion object {
        const val EXTRA_START_CAPTURE = "start_capture"
    }

    private var pendingCapture = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        startForeground(NotificationHelper.NOTIF_ID, NotificationHelper.buildActionNotification(this))

        ProjectionHolder.loadPermissionData(this)
        if (!ProjectionHolder.hasProjection() && ProjectionHolder.hasSavedPermission()) {
            createMediaProjection()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_START_CAPTURE, false) == true) {
            pendingCapture = true
        }

        if (!ProjectionHolder.hasProjection() && ProjectionHolder.hasSavedPermission()) {
            createMediaProjection()
        }

        tryStartCaptureIfReady()
        return START_STICKY
    }

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
            tryStartCaptureIfReady()
        } catch (e: Exception) {
            Log.e("MediaProjectionSvc", "Failed to create MediaProjection", e)
        }
    }

    private fun tryStartCaptureIfReady() {
        if (pendingCapture && ProjectionHolder.hasProjection()) {
            pendingCapture = false
            startService(Intent(this, CaptureOverlayService::class.java))
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationHelper.cancelNotification(this)
        ProjectionHolder.clear()
        super.onDestroy()
    }
}