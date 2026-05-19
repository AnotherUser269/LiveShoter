package com.example.liveshoter.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Делает полноэкранный скриншот, используя [MediaProjection].
 * Создаёт виртуальный дисплей и читает кадр с задержкой в 350 мс,
 * чтобы гарантировать готовность изображения.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("ScreenCapture", "MediaProjection stopped")
            release()
        }
    }

    /**
     * Запускает захват и передаёт полученный [Bitmap] в [onCaptured].
     */
    fun capture(onCaptured: (Bitmap) -> Unit) {
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        // Небольшая задержка для гарантированного обновления буфера виртуального дисплея
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image: Image? = imageReader?.acquireLatestImage()
                if (image == null) {
                    Log.e("ScreenCapture", "Captured image is null")
                    release()
                    return@postDelayed
                }

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                onCaptured(bitmap)
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Capture failed", e)
            } finally {
                release()
            }
        }, 350)
    }

    /**
     * Освобождает виртуальный дисплей, ImageReader и отменяет колбэк проекции.
     */
    private fun release() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e("ScreenCapture", "VirtualDisplay release failed", e)
        }
        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("ScreenCapture", "ImageReader close failed", e)
        }
        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (e: Exception) {
            Log.e("ScreenCapture", "MediaProjection callback unregister failed", e)
        }

        virtualDisplay = null
        imageReader = null
    }
}