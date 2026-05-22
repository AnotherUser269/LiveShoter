package com.example.liveshoter

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.liveshoter.capture.MediaProjectionService
import com.example.liveshoter.capture.ProjectionHolder
import com.example.liveshoter.notifications.NotificationHelper
import com.example.liveshoter.ui.navigation.AppNavHost
import com.example.liveshoter.ui.theme.LiveShoterTheme

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Лаунчер для результата запроса MediaProjection
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                ProjectionHolder.savePermissionData(this, result.resultCode, result.data!!)
                // После получения проекции запускаем сервис (разрешения к этому моменту уже есть)
                startMediaProjectionService()
            }
        }

    // Лаунчер для разрешения POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Разрешение получено, выполняем отложенное действие
                when (pendingAction) {
                    PendingAction.START_SERVICE -> startMediaProjectionService()
                    PendingAction.REQUEST_PROJECTION -> requestScreenCapture()
                    null -> {}
                }
            } else {
                Toast.makeText(
                    this,
                    "Без разрешения на уведомления сервис не сможет работать",
                    Toast.LENGTH_LONG
                ).show()
            }
            pendingAction = null
        }

    // Действие, которое нужно выполнить после получения разрешения на уведомления
    private enum class PendingAction { START_SERVICE, REQUEST_PROJECTION }
    private var pendingAction: PendingAction? = null

    private var launchProjectionOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Создаём канал уведомлений
        NotificationHelper.createChannel(this)

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Загружаем сохранённые данные о разрешении на запись экрана
        ProjectionHolder.loadPermissionData(this)

        // Если разрешение экрана уже выдано – пытаемся запустить сервис, но сначала проверяем другие разрешения
        if (ProjectionHolder.hasSavedPermission()) {
            checkOverlayAndNotification { startMediaProjectionService() }
        }

        // Обрабатываем флаг из уведомления (пользователь нажал Capture, но нет сохранённого разрешения)
        if (intent.getBooleanExtra("request_projection", false) &&
            !ProjectionHolder.hasSavedPermission()
        ) {
            checkOverlayAndNotification { requestScreenCapture() }
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
     * Проверяет разрешение на оверлей, затем на уведомления.
     * После успешной проверки выполняет [onAllGranted].
     */
    private fun checkOverlayAndNotification(onAllGranted: () -> Unit) {
        if (!hasOverlayPermission()) {
            // Запоминаем действие, которое нужно выполнить после получения оверлея
            // (оно будет выполнено в onResume после возврата из настроек)
            pendingAction = if (ProjectionHolder.hasSavedPermission()) {
                PendingAction.START_SERVICE
            } else {
                PendingAction.REQUEST_PROJECTION
            }
            requestOverlayPermission()
        } else {
            checkNotificationPermission(onAllGranted)
        }
    }

    /**
     * Проверяет разрешение на уведомления (Android 13+) и выполняет [onGranted].
     */
    private fun checkNotificationPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Запоминаем, что нужно сделать после получения разрешения
            pendingAction = when {
                ProjectionHolder.hasSavedPermission() -> PendingAction.START_SERVICE
                else -> PendingAction.REQUEST_PROJECTION
            }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onGranted()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
        }
    }

    /**
     * Вызывается после того, как пользователь вернулся в приложение из настроек оверлея.
     * Если разрешение теперь есть – продолжаем цепочку.
     */
    override fun onResume() {
        super.onResume()

        // Если мы ждали возврата из настроек оверлея
        if (pendingAction != null && hasOverlayPermission()) {
            // Продолжаем: теперь проверяем уведомления
            checkNotificationPermission {
                when (pendingAction) {
                    PendingAction.START_SERVICE -> startMediaProjectionService()
                    PendingAction.REQUEST_PROJECTION -> requestScreenCapture()
                    null -> {}
                }
                pendingAction = null
            }
        }

        // Дополнительный флаг для случая, когда запуск проекции откладывался
        if (launchProjectionOnResume && hasOverlayPermission()) {
            launchProjectionOnResume = false
            requestScreenCapture()
        }
    }

    /**
     * Запускает системный диалог запроса разрешения на захват экрана.
     */
    private fun requestScreenCapture() {
        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    /**
     * Запускает MediaProjectionService (foreground).
     */
    private fun startMediaProjectionService() {
        val intent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}