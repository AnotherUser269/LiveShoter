package com.example.liveshoter.ui.screens.staticEditorScreen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri

/**
 * ViewModel для редактора изображений с возможностью рисования.
 *
 * Хранит текущее изображение, нарисованные штрихи и параметры кисти.
 * Предоставляет методы для управления штрихами и экспорта в Bitmap/галерею.
 */
class StaticEditorViewModel : ViewModel() {

    private val viewModelJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    // URI текущего изображения
    var imageUri by mutableStateOf<Uri?>(null)
        private set

    // Список всех сохранённых штрихов (точки нормализованы относительно изображения)
    var strokes = mutableStateListOf<Stroke>()
        private set

    // Текущий рисуемый путь (нормализованные точки)
    var currentPath by mutableStateOf<List<Offset>>(emptyList())
        private set

    // Цвет и размер кисти
    var currentColor by mutableStateOf(Color.Red)
    var brushSize by mutableFloatStateOf(8f) // Логический размер кисти (масштабируется при экспорте)

    /** Устанавливает новое изображение и сбрасывает штрихи */
    fun setImage(uri: Uri?) {
        imageUri = uri
        strokes.clear()
        currentPath = emptyList()
    }

    /** Начало нового пути (точка нормализована 0..1) */
    fun startPath(normPoint: Offset) {
        currentPath = listOf(normPoint)
    }

    /** Добавляет точку в текущий путь */
    fun addPoint(normPoint: Offset) {
        currentPath = currentPath + normPoint
    }

    /** Завершение текущего пути и добавление его в список штрихов */
    fun endPath() {
        if (currentPath.size > 1) {
            strokes.add(
                Stroke(
                    points = currentPath,
                    color = currentColor,
                    width = brushSize
                )
            )
        }
        currentPath = emptyList()
    }

    /** Удаление последнего штриха */
    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    /** Полная очистка всех штрихов */
    fun clear() {
        strokes.clear()
        currentPath = emptyList()
    }

    /**
     * Экспорт изображения в Bitmap с учетом EXIF-ориентации и нарисованных штрихов.
     * @param intrinsicSize — исходный размер изображения (для корректного масштабирования штрихов)
     */
    fun exportBitmap(context: Context, intrinsicSize: Size): Bitmap? {
        val uri = imageUri ?: return null
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null

        // Получаем ориентацию EXIF
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        // Поворот изображения при необходимости
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else original

        if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) return null

        val bmp = createBitmap(rotated.width, rotated.height)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(rotated, 0f, 0f, null)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Масштабирование нормализованных координат к пикселям
        val scaleX = rotated.width.toFloat() / intrinsicSize.width
        val scaleY = rotated.height.toFloat() / intrinsicSize.height
        val avgScale = (scaleX + scaleY) * 0.5f

        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.width * avgScale
            for (i in 1 until stroke.points.size) {
                val p1 = stroke.points[i - 1]
                val p2 = stroke.points[i]
                val x1 = p1.x * intrinsicSize.width * scaleX
                val y1 = p1.y * intrinsicSize.height * scaleY
                val x2 = p2.x * intrinsicSize.width * scaleX
                val y2 = p2.y * intrinsicSize.height * scaleY
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }

        return bmp
    }

    fun generateFileNameFromPattern(pattern: String): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        return pattern.replace("{time}", time)
    }

    fun saveToGalleryAsync(
        context: Context,
        bitmap: Bitmap,
        saveUriString: String?,          // из настроек
        fileNamePattern: String?,        // из настроек
        onComplete: ((Uri?) -> Unit)? = null
    ) {
        ioScope.launch {
            var savedUri: Uri? = null
            try {
                val name = generateFileNameFromPattern(fileNamePattern ?: "editor_{time}") + ".png"

                if (!saveUriString.isNullOrBlank() && saveUriString.startsWith("content://")) {
                    // SAF tree uri: создаём файл в выбранной папке
                    val treeUri = saveUriString.toUri()
                    val doc = DocumentFile.fromTreeUri(context, treeUri)
                    val file = doc?.createFile("image/png", name)
                    file?.uri?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        }
                        savedUri = file.uri
                    }
                } else {
                    // MediaStore RELATIVE_PATH (включая когда saveUriString = "Pictures" или null)
                    val relative = if (saveUriString.isNullOrBlank()) "Pictures/LiveShoter" else saveUriString
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, relative)
                    }

                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        }
                        savedUri = uri
                    }
                }
            } catch (e: Exception) {
                savedUri = null
            }

            withContext(Dispatchers.Main) {
                onComplete?.invoke(savedUri)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}

/** Модель штриха: список нормализованных точек, цвет и ширина */
data class Stroke(
    val points: List<Offset>, // нормализованные: 0..1
    val color: Color,
    val width: Float
)