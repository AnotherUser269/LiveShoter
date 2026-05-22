package com.example.liveshoter.ui.screens.dynamicEditorScreen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DynamicEditorViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    var imageUri by mutableStateOf<Uri?>(null)
    var strokes = mutableStateListOf<Stroke>()
    var currentColor by mutableStateOf(Color.Red)
    var brushSize by mutableFloatStateOf(8f)
    var displayScale by mutableFloatStateOf(1f)
    var currentPath by mutableStateOf<List<Offset>>(emptyList())

    enum class RecordingState { Idle, Recording, Processing }
    var recordingState by mutableStateOf(RecordingState.Idle)
    var elapsedSeconds by mutableIntStateOf(0)
    var lastSavedUri by mutableStateOf<Uri?>(null)

    private var mediaRecorder: MediaRecorder? = null
    private var inputSurface: Surface? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var recordingFps = 8

    // Настройки сохранения, переданные при старте записи
    private var saveUriSetting: String? = null
    private var fileNamePatternSetting: String? = null

    init {
        savedStateHandle.get<String>("imageUri")?.let { imageUri = it.toUri() }
        val cnt = savedStateHandle.get<Int>("strokesCount") ?: 0
        strokes.clear()
        for (i in 0 until cnt) {
            val pts = savedStateHandle.get<String>("stroke_${i}_points")
            val color = savedStateHandle.get<Int>("stroke_${i}_color")
            val width = savedStateHandle.get<Float>("stroke_${i}_width")
            val isDot = savedStateHandle.get<Boolean>("stroke_${i}_isDot") ?: false
            val scale = savedStateHandle.get<Float>("stroke_${i}_displayScale") ?: 1f
            if (pts != null && color != null && width != null) {
                strokes.add(Stroke(pts.parseOffsetList(), Color(color), width, isDot, scale))
            }
        }
        savedStateHandle.get<Int>("currentColor")?.let { currentColor = Color(it) }
        savedStateHandle.get<Float>("brushSize")?.let { brushSize = it }
        savedStateHandle.get<String>("lastSavedUri")?.let { lastSavedUri = it.toUri() }
    }

    private fun saveState() {
        savedStateHandle["imageUri"] = imageUri.toString()
        savedStateHandle["strokesCount"] = strokes.size
        strokes.forEachIndexed { i, s ->
            savedStateHandle["stroke_${i}_points"] = s.points.joinToString(";") { "${it.x},${it.y}" }
            savedStateHandle["stroke_${i}_color"] = s.color.toArgb()
            savedStateHandle["stroke_${i}_width"] = s.width
            savedStateHandle["stroke_${i}_isDot"] = s.isDot
            savedStateHandle["stroke_${i}_displayScale"] = s.displayScale
        }
        savedStateHandle["currentColor"] = currentColor.toArgb()
        savedStateHandle["brushSize"] = brushSize
        savedStateHandle["lastSavedUri"] = lastSavedUri.toString()
    }

    fun setImage(uri: Uri?) {
        imageUri = uri
        strokes.clear(); currentPath = emptyList()
        saveState()
    }

    fun startPath(normPoint: Offset) { currentPath = listOf(normPoint) }
    fun addPoint(normPoint: Offset) { currentPath = currentPath + normPoint }
    fun endPath() {
        if (currentPath.isNotEmpty()) {
            strokes.add(Stroke(currentPath, currentColor, brushSize, isDot = currentPath.size == 1, displayScale = displayScale))
            currentPath = emptyList()
            saveState()
        }
    }

    fun addTapPoint(normPoint: Offset) {
        strokes.add(Stroke(listOf(normPoint), currentColor, brushSize, isDot = true, displayScale = displayScale))
        saveState()
    }

    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); saveState() }
    fun clear() { strokes.clear(); currentPath = emptyList(); saveState() }

    fun startRecording(context: Context, saveUri: String?, fileNamePattern: String?) {
        if (recordingState != RecordingState.Idle) return

        // Сохраняем настройки
        saveUriSetting = saveUri
        fileNamePatternSetting = fileNamePattern

        recordingFps = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt("fps", 8).coerceIn(1, 15)

        val baseBitmap = loadImageBitmap(context) ?: return
        val originalW = baseBitmap.width.toFloat()
        val originalH = baseBitmap.height.toFloat()
        val imgAspect = originalW / originalH
        val boxSize = 480
        if (imgAspect > 1f) {
            videoWidth = boxSize
            videoHeight = (boxSize / imgAspect).toInt()
        } else {
            videoHeight = boxSize
            videoWidth = (boxSize * imgAspect).toInt()
        }
        // Выравниваем размеры: чётные и кратные 16
        videoWidth = (videoWidth + 15) / 16 * 16
        videoHeight = (videoHeight + 15) / 16 * 16
        videoWidth = maxOf(videoWidth, 64)
        videoHeight = maxOf(videoHeight, 64)

        // Масштабируем изображение под выровненные размеры
        val scaledBase = baseBitmap.scale(videoWidth, videoHeight) // сохраняет пропорции
        baseBitmap.recycle()

        val scaledW = scaledBase.width.toFloat()
        val scaledH = scaledBase.height.toFloat()
        val imgLeft = (videoWidth - scaledW) / 2f
        val imgTop = (videoHeight - scaledH) / 2f
        val videoScale = scaledW / originalW   // одинаково для ширины и высоты

        elapsedSeconds = 0
        lastSavedUri = null
        saveState()
        recordingState = RecordingState.Recording

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(context.cacheDir, "recording_$timeStamp.mp4")

        try {
            mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(videoWidth, videoHeight)
                setVideoFrameRate(recordingFps)
                setVideoEncodingBitRate(2_500_000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            inputSurface = mediaRecorder!!.surface
        } catch (e: Exception) {
            Log.e("DynamicEditor", "MediaRecorder prepare failed", e)
            mediaRecorder?.release()
            mediaRecorder = null
            scaledBase.recycle()
            recordingState = RecordingState.Idle
            return
        }

        timerJob = viewModelScope.launch {
            while (isActive) { delay(1000); elapsedSeconds++ }
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val frameDelay = 1000L / recordingFps

                while (isActive) {
                    val surface = inputSurface ?: break
                    val canvas = surface.lockCanvas(null)
                    canvas.drawColor(Color.Black.toArgb())
                    canvas.drawBitmap(scaledBase, imgLeft, imgTop, null)

                    val (strokesCopy, pathCopy, colorCopy, sizeCopy, scaleCopy) =
                        withContext(Dispatchers.Main) {
                            Quintuple(strokes.toList(), currentPath.toList(), currentColor, brushSize, displayScale)
                        }

                    for (s in strokesCopy) {
                        paint.color = s.color.toArgb()
                        val physicalWidth = if (s.displayScale > 0f) s.width / s.displayScale else s.width
                        val videoStrokeWidth = physicalWidth * videoScale
                        paint.strokeWidth = videoStrokeWidth

                        if (s.isDot && s.points.size == 1) {
                            val px = imgLeft + s.points[0].x * scaledW
                            val py = imgTop + s.points[0].y * scaledH
                            canvas.drawCircle(px, py, videoStrokeWidth / 2f, paint)
                        } else if (s.points.size >= 2) {
                            for (i in 1 until s.points.size) {
                                val p1 = s.points[i-1]
                                val p2 = s.points[i]
                                val x1 = imgLeft + p1.x * scaledW
                                val y1 = imgTop + p1.y * scaledH
                                val x2 = imgLeft + p2.x * scaledW
                                val y2 = imgTop + p2.y * scaledH
                                canvas.drawLine(x1, y1, x2, y2, paint)
                            }
                        }
                    }

                    if (pathCopy.isNotEmpty()) {
                        paint.color = colorCopy.toArgb()
                        val physicalWidth = if (scaleCopy > 0f) sizeCopy / scaleCopy else sizeCopy
                        val videoStrokeWidth = physicalWidth * videoScale
                        paint.strokeWidth = videoStrokeWidth

                        if (pathCopy.size == 1) {
                            val px = imgLeft + pathCopy[0].x * scaledW
                            val py = imgTop + pathCopy[0].y * scaledH
                            canvas.drawCircle(px, py, videoStrokeWidth / 2f, paint)
                        } else {
                            for (i in 1 until pathCopy.size) {
                                val p1 = pathCopy[i-1]
                                val p2 = pathCopy[i]
                                val x1 = imgLeft + p1.x * scaledW
                                val y1 = imgTop + p1.y * scaledH
                                val x2 = imgLeft + p2.x * scaledW
                                val y2 = imgTop + p2.y * scaledH
                                canvas.drawLine(x1, y1, x2, y2, paint)
                            }
                        }
                    }

                    surface.unlockCanvasAndPost(canvas)
                    delay(frameDelay)
                }
            } catch (_: CancellationException) {} catch (e: Exception) {
                Log.e("DynamicEditor", "Recording error", e)
            } finally {
                scaledBase.recycle()
            }
        }
    }

    fun stopRecording(context: Context) {
        if (recordingState != RecordingState.Recording) return
        recordingState = RecordingState.Processing
        timerJob?.cancel(); recordingJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(300)
                mediaRecorder?.stop(); mediaRecorder?.release()
                inputSurface = null; mediaRecorder = null
                outputFile?.let { file ->
                    if (file.exists()) {
                        val uri = saveVideoToGallery(context, file)
                        withContext(Dispatchers.Main) {
                            lastSavedUri = uri
                            saveState()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DynamicEditor", "Stop failed", e)
            } finally {
                withContext(Dispatchers.Main) { recordingState = RecordingState.Idle }
            }
        }
    }

    fun shareVideo(context: Context) {
        val uri = lastSavedUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }

    fun onToastShown() {
        lastSavedUri = null
        saveState()
    }

    // Генерация имени файла по шаблону
    private fun generateFileNameFromPattern(pattern: String?): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val basePattern = pattern ?: "recording_{time}"
        return basePattern.replace("{time}", time) + ".mp4"
    }

    // Сохранение видео с учётом настроек (папка, шаблон имени)
    private suspend fun saveVideoToGallery(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val name = generateFileNameFromPattern(fileNamePatternSetting)

        // Если указан saveUri (content://), сохраняем через DocumentFile в выбранную папку
        if (!saveUriSetting.isNullOrBlank() && saveUriSetting!!.startsWith("content://")) {
            val treeUri = saveUriSetting!!.toUri()
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val videoFile = doc?.createFile("video/mp4", name)
            videoFile?.uri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    file.inputStream().copyTo(os)
                }
                file.delete()
                return@withContext uri
            }
            return@withContext null
        }

        // Стандартное сохранение через MediaStore
        val relativePath = if (saveUriSetting.isNullOrBlank()) {
            "${Environment.DIRECTORY_MOVIES}/LiveShoter"
        } else {
            saveUriSetting!!
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    file.inputStream().copyTo(os)
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                file.delete()
                return@withContext uri
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), relativePath)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, name)
            file.copyTo(target, overwrite = true)
            file.delete()
            return@withContext Uri.fromFile(target)
        }
        return@withContext null
    }

    private fun loadImageBitmap(context: Context): Bitmap? {
        val uri = imageUri ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { Log.e("DynamicEditor", "Load image failed", e); null }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel(); timerJob?.cancel(); mediaRecorder?.release()
    }

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}

data class Stroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isDot: Boolean = false,
    val displayScale: Float = 1f
)

private fun String.parseOffsetList(): List<Offset> =
    split(";").map { coord ->
        val (x, y) = coord.split(",").map { it.toFloat() }
        Offset(x, y)
    }