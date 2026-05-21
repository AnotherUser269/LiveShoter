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
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class StaticEditorViewModel : ViewModel() {

    private val viewModelJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    var imageUri by mutableStateOf<Uri?>(null)
        private set

    var strokes = mutableStateListOf<Stroke>()
        private set

    var currentPath by mutableStateOf<List<Offset>>(emptyList())
        private set

    var currentColor by mutableStateOf(Color.Red)
    var brushSize by mutableFloatStateOf(8f)
    var displayScale by mutableFloatStateOf(1f)

    fun setImage(uri: Uri?) {
        imageUri = uri
        strokes.clear()
        currentPath = emptyList()
    }

    fun startPath(normPoint: Offset) {
        currentPath = listOf(normPoint)
    }

    fun addPoint(normPoint: Offset) {
        currentPath = currentPath + normPoint
    }

    fun endPath() {
        if (currentPath.isNotEmpty()) {
            strokes.add(
                Stroke(
                    points = currentPath,
                    color = currentColor,
                    width = brushSize,
                    isDot = currentPath.size == 1   // одна точка - флаг isDot
                )
            )
        }
        currentPath = emptyList()
    }

    /** Обработка одиночного касания (тап) */
    fun addTapPoint(normPoint: Offset) {
        strokes.add(
            Stroke(
                points = listOf(normPoint),
                color = currentColor,
                width = brushSize,
                isDot = true
            )
        )
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    fun clear() {
        strokes.clear()
        currentPath = emptyList()
    }

    /**
     * Экспорт в Bitmap с учётом EXIF-поворота и масштаба экрана.
     * @param intrinsicSize исходный размер изображения (до поворота)
     */
    fun exportBitmap(context: Context, intrinsicSize: Size): Bitmap? {
        val uri = imageUri ?: return null
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null

        // Определяем поворот из EXIF
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else original

        val rw = rotated.width.toFloat()
        val rh = rotated.height.toFloat()
        if (rw <= 0f || rh <= 0f) return null

        val bmp = createBitmap(rotated.width, rotated.height)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(rotated, 0f, 0f, null)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Функция пересчёта нормализованной точки (0..1 относительно intrinsicSize) в координаты повёрнутого изображения
        fun normalizedToRotated(nx: Float, ny: Float): Pair<Float, Float> {
            return when (rotation) {
                90  -> Pair(ny, 1f - nx)
                180 -> Pair(1f - nx, 1f - ny)
                270 -> Pair(1f - ny, nx)
                else -> Pair(nx, ny)
            }
        }

        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            val scaledWidth = if (displayScale > 0f) stroke.width / displayScale else stroke.width
            paint.strokeWidth = scaledWidth

            if (stroke.isDot && stroke.points.size == 1) {
                // Рисуем точку как круг
                val (nx, ny) = normalizedToRotated(stroke.points[0].x, stroke.points[0].y)
                val cx = nx * rw
                val cy = ny * rh
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, scaledWidth / 2f, paint)
                paint.style = Paint.Style.STROKE // возвращаем обратно
            } else {
                // Линии
                for (i in 1 until stroke.points.size) {
                    val p1 = stroke.points[i - 1]
                    val p2 = stroke.points[i]
                    val (nx1, ny1) = normalizedToRotated(p1.x, p1.y)
                    val (nx2, ny2) = normalizedToRotated(p2.x, p2.y)

                    val x1 = nx1 * rw
                    val y1 = ny1 * rh
                    val x2 = nx2 * rw
                    val y2 = ny2 * rh
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
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
        saveUriString: String?,
        fileNamePattern: String?,
        onComplete: ((Uri?) -> Unit)? = null
    ) {
        ioScope.launch {
            var savedUri: Uri? = null
            try {
                val name = generateFileNameFromPattern(fileNamePattern ?: "editor_{time}") + ".png"

                if (!saveUriString.isNullOrBlank() && saveUriString.startsWith("content://")) {
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
                    val relative = saveUriString ?: "Pictures/LiveShoter"
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
            } catch (_: Exception) {
                savedUri = null
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(savedUri)
            }
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, intrinsicSize: Size) {
        try {
            val file = File(context.cacheDir, "shared_screenshot.png")
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            val uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share image"))
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}

data class Stroke(
    val points: List<Offset>, // нормализованные: 0..1 относительно intrinsicSize
    val color: Color,
    val width: Float,
    val isDot: Boolean = false
)