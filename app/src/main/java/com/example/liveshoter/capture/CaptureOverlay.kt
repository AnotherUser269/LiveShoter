package com.example.liveshoter.capture

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * Оверлей для выбора прямоугольной области экрана.
 * Затемняет всё окно и позволяет пользователю выделить область касанием.
 * После отпускания пальца передаёт координаты [Rect] в [onCaptureDone].
 */
class CaptureOverlay(
    context: Context,
    private val onCaptureDone: (Rect) -> Unit
) : View(context) {

    // Полупрозрачное затемнение фона
    private val dimPaint = Paint().apply {
        color = "#88000000".toColorInt()
    }

    // Кисть, вырезающая прозрачное окно в затемнении
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Белая рамка выделенной области
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isSelecting = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Заливка всего холста полупрозрачным цветом
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Если идёт выделение, вырезаем внутренний прямоугольник и рисуем рамку
        if (isSelecting) {
            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)

            canvas.drawRect(left, top, right, bottom, clearPaint)
            canvas.drawRect(left, top, right, bottom, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isSelecting = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                endX = event.x
                endY = event.y

                val rect = Rect(
                    minOf(startX, endX).toInt(),
                    minOf(startY, endY).toInt(),
                    maxOf(startX, endX).toInt(),
                    maxOf(startY, endY).toInt()
                )

                isSelecting = false
                invalidate()
                onCaptureDone(rect)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}