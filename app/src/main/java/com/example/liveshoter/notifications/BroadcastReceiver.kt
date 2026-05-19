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

/**
 * Приёмник действий из управляющего уведомления [NotificationHelper].
 * Обрабатывает нажатия кнопок «Capture» и «Exit».
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationHelper.ACTION_CAPTURE -> {
                handleCaptureAction(context)
            }
            NotificationHelper.ACTION_EXIT -> {
                handleExitAction(context)
            }
        }
    }

    /**
     * Обрабатывает нажатие кнопки «Capture».
     *
     *  1. Проверяет разрешение на оверлей (SYSTEM_ALERT_WINDOW). Если нет – открывает настройки.
     *  2. Проверяет наличие сохранённых данных разрешения на захват экрана. Если нет – открывает MainActivity с запросом.
     *  3. Если активный MediaProjection отсутствует, перезапускает [MediaProjectionService] и даёт ему время на инициализацию.
     *  4. Запускает [CaptureOverlayService] для отображения оверлея и последующего скриншота.
     */
    private fun handleCaptureAction(context: Context) {
        // Проверка разрешения на отображение поверх других окон
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

        // Проверка наличия сохранённого разрешения на запись экрана
        if (!ProjectionHolder.hasSavedPermission()) {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("request_projection", true)
            }
            context.startActivity(activityIntent)
            return
        }

        // Если проекция не активна, запускаем сервис-держатель и ждём создания
        if (!ProjectionHolder.hasProjection()) {
            val holderIntent = Intent(context, MediaProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(holderIntent)
            } else {
                context.startService(holderIntent)
            }
            // Короткая пауза для инициализации MediaProjection внутри сервиса
            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) { }
        }

        // Запуск оверлея (обычный сервис, не foreground)
        val serviceIntent = Intent(context, CaptureOverlayService::class.java)
        context.startService(serviceIntent)
    }

    /**
     * Обрабатывает нажатие кнопки «Exit».
     * Останавливает сервисы захвата и удержания проекции,
     * очищает сохранённые разрешения и убирает управляющее уведомление.
     */
    private fun handleExitAction(context: Context) {
        context.stopService(Intent(context, CaptureOverlayService::class.java))
        context.stopService(Intent(context, MediaProjectionService::class.java))
        ProjectionHolder.clearPreferences(context)
        NotificationHelper.cancelNotification(context)
    }
}