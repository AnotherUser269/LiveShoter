package com.example.liveshoter.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Сохраняет [Bitmap] в общедоступную папку Pictures/RawScreenshots.
 * На Android 10+ используется MediaStore, на более ранних версиях —
 * прямое сохранение на внешнее хранилище (требуется разрешение WRITE_EXTERNAL_STORAGE).
 */
object ImageSaver {

    /**
     * Сохраняет переданный [bitmap] в формате PNG.
     *
     * @return [File] с информацией о сохранённом изображении либо null в случае ошибки.
     */
    fun saveBitmap(context: Context, bitmap: Bitmap): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "Screenshot_$timeStamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, bitmap, displayName)
        } else {
            return saveDirectlyToFile(displayName, bitmap)
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, displayName: String): File? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RawScreenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            // Возвращается условный File, реальный путь можно получить через MediaStore
            return File(uri.toString())
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun saveDirectlyToFile(displayName: String, bitmap: Bitmap): File? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "RawScreenshots"
        )
        if (!dir.exists() && !dir.mkdirs()) return null

        val file = File(dir, displayName)
        return try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            file
        } catch (e: Exception) {
            null
        }
    }
}