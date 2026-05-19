package com.example.liveshoter.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.liveshoter.MainActivity

/**
 * Сервис, который поверх всех окон показывает оверлей [CaptureOverlay] для выбора
 * прямоугольной области экрана, выполняет её скриншот и сохраняет результат.
 *
 * Запускается как обычный сервис (не foreground). Предполагает, что
 * [ProjectionHolder.mediaProjection] уже создан сторонним сервисом.
 */
class CaptureOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlay: CaptureOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        // Проекция должна быть активна; иначе завершаемся с сообщением
        if (!ProjectionHolder.hasProjection()) {
            Toast.makeText(
                this,
                "Capture permission lost. Please restart capture.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    /**
     * Добавляет [CaptureOverlay] в оконный менеджер в качестве системного оверлея.
     */
    private fun showOverlay() {
        if (overlay != null) return

        overlay = CaptureOverlay(this) { rect ->
            captureAndSave(rect)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager.addView(overlay, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    /**
     * Убирает оверлей, выполняет захват выбранной области [rect]
     * и сохраняет скриншот через [ImageSaver]. После завершения возвращает
     * приложение на передний план и останавливает сервис.
     */
    private fun captureAndSave(rect: Rect) {
        removeOverlay()

        val mediaProjection = ProjectionHolder.mediaProjection
        if (mediaProjection == null) {
            Toast.makeText(this, "Capture permission lost", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        ScreenCaptureManager(this, mediaProjection).capture { bitmap ->
            try {
                val cropped = Bitmap.createBitmap(
                    bitmap, rect.left, rect.top,
                    rect.width(), rect.height()
                )
                val file = ImageSaver.saveBitmap(this, cropped)
                if (file != null) {
                    Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // Возвращаем основную активность на передний план
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
            stopSelf()
        }
    }

    /**
     * Удаляет оверлей из оконного менеджера.
     */
    private fun removeOverlay() {
        overlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
            overlay = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}