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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri
import androidx.core.graphics.scale

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

    init {
        savedStateHandle.get<String>("imageUri")?.let { imageUri = it.toUri() }
        val cnt = savedStateHandle.get<Int>("strokesCount") ?: 0
        strokes.clear()
        for (i in 0 until cnt) {
            val pts = savedStateHandle.get<String>("stroke_${i}_points")
            val color = savedStateHandle.get<Int>("stroke_${i}_color")
            val width = savedStateHandle.get<Float>("stroke_${i}_width")
            val isDot = savedStateHandle.get<Boolean>("stroke_${i}_isDot") ?: false
            if (pts != null && color != null && width != null) {
                strokes.add(Stroke(pts.parseOffsetList(), Color(color), width, isDot))
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
            strokes.add(Stroke(currentPath, currentColor, brushSize, isDot = currentPath.size == 1))
            currentPath = emptyList()
            saveState()
        }
    }

    fun addTapPoint(normPoint: Offset) {
        strokes.add(Stroke(listOf(normPoint), currentColor, brushSize, isDot = true))
        saveState()
    }

    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); saveState() }
    fun clear() { strokes.clear(); currentPath = emptyList(); saveState() }

    // ---------- Исправленная запись видео (размеры выравниваются) ----------
    fun startRecording(context: Context) {
        if (recordingState != RecordingState.Idle) return
        recordingFps = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt("fps", 8).coerceIn(1, 15)

        val baseBitmap = loadImageBitmap(context) ?: return
        val imgAspect = baseBitmap.width.toFloat() / baseBitmap.height.toFloat()
        val boxSize = 480
        if (imgAspect > 1f) {
            videoWidth = boxSize
            videoHeight = (boxSize / imgAspect).toInt()
        } else {
            videoHeight = boxSize
            videoWidth = (boxSize * imgAspect).toInt()
        }
        // Выравниваем размеры: чётные и кратные 16 (требование кодека)
        videoWidth = (videoWidth + 15) / 16 * 16
        videoHeight = (videoHeight + 15) / 16 * 16
        videoWidth = maxOf(videoWidth, 64)
        videoHeight = maxOf(videoHeight, 64)
        baseBitmap.recycle()

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
            recordingState = RecordingState.Idle
            return
        }

        timerJob = viewModelScope.launch {
            while (isActive) { delay(1000); elapsedSeconds++ }
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val scaledBase =
                    (loadImageBitmap(context) ?: return@launch).scale(videoWidth, videoHeight)
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
                    val imgLeft = (videoWidth - scaledBase.width) / 2f
                    val imgTop = (videoHeight - scaledBase.height) / 2f
                    canvas.drawBitmap(scaledBase, imgLeft, imgTop, null)

                    val (strokesCopy, pathCopy, colorCopy, sizeCopy) =
                        withContext(Dispatchers.Main) {
                            Quadruple(strokes.toList(), currentPath.toList(), currentColor, brushSize)
                        }

                    val scale = minOf(videoWidth.toFloat() / scaledBase.width, videoHeight.toFloat() / scaledBase.height)

                    for (s in strokesCopy) {
                        paint.color = s.color.toArgb()
                        paint.strokeWidth = s.width * scale
                        if (s.isDot) {
                            val p = s.points[0]
                            canvas.drawCircle(p.x * videoWidth, p.y * videoHeight, paint.strokeWidth / 2f, paint)
                        } else if (s.points.size >= 2) {
                            for (i in 1 until s.points.size) {
                                canvas.drawLine(s.points[i-1].x * videoWidth, s.points[i-1].y * videoHeight,
                                    s.points[i].x * videoWidth, s.points[i].y * videoHeight, paint)
                            }
                        }
                    }
                    if (pathCopy.isNotEmpty()) {
                        paint.color = colorCopy.toArgb()
                        paint.strokeWidth = sizeCopy * scale
                        if (pathCopy.size == 1) {
                            val p = pathCopy[0]
                            canvas.drawCircle(p.x * videoWidth, p.y * videoHeight, paint.strokeWidth / 2f, paint)
                        } else {
                            for (i in 1 until pathCopy.size) {
                                canvas.drawLine(pathCopy[i-1].x * videoWidth, pathCopy[i-1].y * videoHeight,
                                    pathCopy[i].x * videoWidth, pathCopy[i].y * videoHeight, paint)
                            }
                        }
                    }
                    surface.unlockCanvasAndPost(canvas)
                    delay(frameDelay)
                }
            } catch (_: CancellationException) {} catch (e: Exception) {
                Log.e("DynamicEditor", "Recording error", e)
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

    fun onToastShown() { }

    private fun saveVideoToGallery(context: Context, file: File): Uri? {
        if (!file.exists()) return null
        val name = file.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LiveShoter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os -> file.inputStream().copyTo(os) }
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                file.delete()
                return uri
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LiveShoter")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, name)
            file.copyTo(target, overwrite = true); file.delete()
            return Uri.fromFile(target)
        }
        return null
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

    private data class Quadruple<A,B,C,D>(val first:A, val second:B, val third:C, val fourth:D)
}

data class Stroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isDot: Boolean = false
)

private fun String.parseOffsetList(): List<Offset> =
    split(";").map { coord ->
        val (x, y) = coord.split(",").map { it.toFloat() }
        Offset(x, y)
    }