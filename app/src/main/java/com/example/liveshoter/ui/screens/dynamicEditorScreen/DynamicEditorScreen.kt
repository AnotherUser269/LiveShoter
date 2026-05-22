package com.example.liveshoter.ui.screens.dynamicEditorScreen

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.liveshoter.ui.theme.uiColor as UIColor
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicEditorScreen(
    navController: NavHostController,
    vm: DynamicEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Лаунчер выбора картинки
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        vm.setImage(uri)
    }

    val painter = rememberAsyncImagePainter(vm.imageUri)
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val intrinsicSize: Size = when (val state = painter.state) {
        is coil.compose.AsyncImagePainter.State.Success -> state.painter.intrinsicSize
        else -> Size.Unspecified
    }

    // Прямоугольник, в котором отображается изображение на Canvas
    val displayImageRect: Rect? = remember(containerSize, intrinsicSize) {
        if (containerSize == IntSize.Zero || intrinsicSize == Size.Unspecified) return@remember null
        if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) return@remember null
        val scale = min(containerSize.width.toFloat() / intrinsicSize.width,
            containerSize.height.toFloat() / intrinsicSize.height)
        vm.displayScale = scale
        val w = intrinsicSize.width * scale; val h = intrinsicSize.height * scale
        val left = (containerSize.width - w) / 2f; val top = (containerSize.height - h) / 2f
        Rect(left, top, left + w, top + h)
    }

    // Преобразования координат экран - изображение
    fun screenToImage(pos: Offset, rect: Rect, size: Size): Offset? {
        if (!rect.contains(pos)) return null
        return Offset((pos.x - rect.left) / rect.width * size.width,
            (pos.y - rect.top) / rect.height * size.height)
    }

    fun screenToImageClamped(pos: Offset, rect: Rect, size: Size): Offset {
        val x = ((pos.x - rect.left) / rect.width * size.width).coerceIn(0f, size.width)
        val y = ((pos.y - rect.top) / rect.height * size.height).coerceIn(0f, size.height)
        return Offset(x, y)
    }

    fun imageToScreen(pos: Offset, rect: Rect, size: Size): Offset =
        Offset(rect.left + pos.x / size.width * rect.width,
            rect.top + pos.y / size.height * rect.height)

    // Показываем Toast только если есть сохранённое видео и тост ещё не был показан
    LaunchedEffect(vm.lastSavedUri, vm.toastShown) {
        if (vm.lastSavedUri != null && !vm.toastShown) {
            Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
            vm.onToastShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    if (vm.recordingState == DynamicEditorViewModel.RecordingState.Recording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Color.Red, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("${vm.elapsedSeconds}s", color = UIColor)
                        }
                    }
                },
                actions = {
                    // Кнопка записи / остановки
                    when (vm.recordingState) {
                        DynamicEditorViewModel.RecordingState.Idle -> {
                            val saveUri = prefs.getString("save_uri", null)
                            val fileNamePattern = prefs.getString("file_name_pattern", null)
                            IconButton(onClick = { vm.startRecording(context, saveUri, fileNamePattern) }) {
                                Icon(Icons.Filled.FiberManualRecord, "Start", tint = Color.Red)
                            }
                        }
                        DynamicEditorViewModel.RecordingState.Recording -> IconButton(onClick = { vm.stopRecording(context) }) {
                            Icon(Icons.Filled.Stop, "Stop", tint = Color.Red)
                        }
                        DynamicEditorViewModel.RecordingState.Processing -> CircularProgressIndicator(Modifier.size(24.dp), color = UIColor, strokeWidth = 2.dp)
                    }
                    // Кнопка Share (появляется после сохранения)
                    if (vm.lastSavedUri != null) {
                        IconButton(onClick = { vm.shareVideo(context) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                    }
                    IconButton(onClick = { vm.undo() }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.Clear, "Clear") }
                    IconButton(onClick = { launcher.launch("image/*") }) { Icon(Icons.Filled.UploadFile, "Open") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = UIColor, navigationIconContentColor = UIColor, actionIconContentColor = UIColor)
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = Color.Black) {
                Column(Modifier.fillMaxWidth().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Палитра цветов
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = if (color == vm.currentColor) 3.dp else 2.dp,
                                        color = if (color == vm.currentColor) Color.White else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { vm.currentColor = color }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Размер кисти
                    Slider(value = vm.brushSize, onValueChange = { vm.brushSize = it }, valueRange = 2f..60f,
                        modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Image(painter = painter, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size })

            // Canvas для рисования
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // Drag – рисование линий с остановкой на границе и продолжением после возврата
                    .pointerInput(vm.imageUri, displayImageRect, intrinsicSize) {
                        if (vm.imageUri == null || displayImageRect == null || intrinsicSize == Size.Unspecified) return@pointerInput

                        var wasInside = false

                        detectDragGestures(
                            onDragStart = { pos ->
                                val imagePos = screenToImage(pos, displayImageRect, intrinsicSize)
                                if (imagePos != null) {
                                    vm.startPath(Offset(imagePos.x / intrinsicSize.width, imagePos.y / intrinsicSize.height))
                                    wasInside = true
                                } else {
                                    wasInside = false
                                }
                            },
                            onDrag = { change, _ ->
                                val insidePoint = screenToImage(change.position, displayImageRect, intrinsicSize)
                                val inside = insidePoint != null

                                if (inside && !wasInside) {
                                    vm.addPoint(Offset(insidePoint.x / intrinsicSize.width, insidePoint.y / intrinsicSize.height))
                                } else if (!inside && wasInside) {
                                    val clamped = screenToImageClamped(change.position, displayImageRect, intrinsicSize)
                                    vm.addPoint(Offset(clamped.x / intrinsicSize.width, clamped.y / intrinsicSize.height))
                                } else if (inside) {
                                    // Обычное движение внутри
                                    vm.addPoint(Offset(insidePoint.x / intrinsicSize.width, insidePoint.y / intrinsicSize.height))
                                }
                                // если !inside && !wasInside — ничего не делаем

                                wasInside = inside
                            },
                            onDragEnd = { vm.endPath() }
                        )
                    }
                    // Tap – одиночная точка (только внутри изображения)
                    .pointerInput(vm.imageUri, displayImageRect, intrinsicSize) {
                        if (vm.imageUri == null || displayImageRect == null || intrinsicSize == Size.Unspecified) return@pointerInput
                        detectTapGestures { pos ->
                            val imagePos = screenToImage(pos, displayImageRect, intrinsicSize)
                            if (imagePos != null) {
                                vm.addTapPoint(Offset(imagePos.x / intrinsicSize.width, imagePos.y / intrinsicSize.height))
                            }
                        }
                    }
            ) {
                val rect = displayImageRect ?: return@Canvas
                val currentScale = if (vm.displayScale > 0f) vm.displayScale else 1f

                // Рисуем все завершённые штрихи
                for (stroke in vm.strokes) {
                    val baseWidth = if (stroke.displayScale > 0f) stroke.width / stroke.displayScale else stroke.width
                    val screenWidth = baseWidth * currentScale

                    if (stroke.isDot) {
                        val pImg = Offset(stroke.points[0].x * intrinsicSize.width, stroke.points[0].y * intrinsicSize.height)
                        val center = imageToScreen(pImg, rect, intrinsicSize)
                        drawCircle(stroke.color, radius = screenWidth / 2f, center = center, style = Fill)
                    } else if (stroke.points.size >= 2) {
                        val path = Path().apply {
                            stroke.points.forEachIndexed { i, p ->
                                val sp = imageToScreen(Offset(p.x * intrinsicSize.width, p.y * intrinsicSize.height), rect, intrinsicSize)
                                if (i == 0) moveTo(sp.x, sp.y) else lineTo(sp.x, sp.y)
                            }
                        }
                        drawPath(path, stroke.color, style = Stroke(width = screenWidth))
                    }
                }
                // Текущий рисуемый путь
                if (vm.currentPath.isNotEmpty()) {
                    val baseWidth = if (currentScale > 0f) vm.brushSize / currentScale else vm.brushSize
                    val screenWidth = baseWidth * currentScale   // равно vm.brushSize

                    if (vm.currentPath.size == 1) {
                        val pImg = Offset(vm.currentPath[0].x * intrinsicSize.width, vm.currentPath[0].y * intrinsicSize.height)
                        drawCircle(vm.currentColor, radius = screenWidth / 2f, center = imageToScreen(pImg, rect, intrinsicSize), style = Fill)
                    } else {
                        val path = Path().apply {
                            vm.currentPath.forEachIndexed { i, p ->
                                val sp = imageToScreen(Offset(p.x * intrinsicSize.width, p.y * intrinsicSize.height), rect, intrinsicSize)
                                if (i == 0) moveTo(sp.x, sp.y) else lineTo(sp.x, sp.y)
                            }
                        }
                        drawPath(path, vm.currentColor, style = Stroke(width = screenWidth))
                    }
                }
            }

            if (vm.imageUri == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select an image", color = Color.Gray)
                }
            }
        }
    }
}