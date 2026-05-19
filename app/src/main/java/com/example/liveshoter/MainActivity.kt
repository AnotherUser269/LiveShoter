package com.example.liveshoter

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.navigation.compose.rememberNavController
import com.example.liveshoter.capture.MediaProjectionService
import com.example.liveshoter.capture.ProjectionHolder
import com.example.liveshoter.notifications.NotificationHelper
import com.example.liveshoter.ui.navigation.AppNavHost
import com.example.liveshoter.ui.theme.LiveShoterTheme

/**
 * Основная активность приложения.
 * Запрашивает разрешение на захват экрана, управляет сервисом удержания MediaProjection
 * и показывает управляющее уведомление с кнопками Capture/Exit.
 */
class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    /**
     * Обработчик результата запроса разрешения на MediaProjection.
     * При успехе сохраняет данные, запускает [MediaProjectionService]
     * и показывает управляющее уведомление.
     */
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                ProjectionHolder.savePermissionData(this, result.resultCode, result.data!!)
                startMediaProjectionService()
                NotificationHelper.showActionNotification(this)
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannel(this)

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Загружаем сохранённые данные о разрешении (если есть)
        ProjectionHolder.loadPermissionData(this)

        // Если разрешение уже было выдано ранее, восстанавливаем сервис и уведомление
        if (ProjectionHolder.hasSavedPermission()) {
            startMediaProjectionService()
            // Показываем управляющее уведомление, если сервис не был запущен (например, после убийства процесса)
            if (!isMediaProjectionServiceRunning()) {
                NotificationHelper.showActionNotification(this)
            }
        }

        // Обрабатываем флаг request_projection (приходит из уведомления, если разрешения нет)
        if (intent.getBooleanExtra("request_projection", false) &&
            !ProjectionHolder.hasSavedPermission()
        ) {
            projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
        intent.removeExtra("request_projection")

        setContent {
            LiveShoterTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }

    /**
     * Запускает [MediaProjectionService] в зависимости от версии API
     * (foreground service начиная с Android O).
     */
    private fun startMediaProjectionService() {
        val intent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Проверяет, работает ли [MediaProjectionService] в данный момент.
     */
    private fun isMediaProjectionServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MediaProjectionService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}