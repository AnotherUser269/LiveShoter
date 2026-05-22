package com.example.liveshoter.ui.screens.staticEditorScreen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.liveshoter.ui.screens.settingsScreen.SettingsScreenViewModel
import com.example.liveshoter.ui.theme.uiColor as UIColor
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaticEditorScreen(
    navController: NavHostController,
    vm: StaticEditorViewModel = viewModel()
) {
    val context = LocalContext.current

    val settingsVm: SettingsScreenViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsScreenViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsScreenViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> vm.setImage(uri) }

    val painter = rememberAsyncImagePainter(vm.imageUri)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val intrinsicSize: Size = when (val state = painter.state) {
        is coil.compose.AsyncImagePainter.State.Success -> state.painter.intrinsicSize
        else -> Size.Unspecified
    }

    // Прямоугольник отображения изображения с сохранением пропорций
    val displayImageRect: Rect? = remember(containerSize, intrinsicSize) {
        if (containerSize == IntSize.Zero || intrinsicSize == Size.Unspecified) return@remember null
        if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) return@remember null

        val scale = min(
            containerSize.width.toFloat() / intrinsicSize.width,
            containerSize.height.toFloat() / intrinsicSize.height
        )
        vm.displayScale = scale

        val scaledWidth = intrinsicSize.width * scale
        val scaledHeight = intrinsicSize.height * scale
        val left = (containerSize.width - scaledWidth) / 2f
        val top = (containerSize.height - scaledHeight) / 2f
        Rect(left, top, left + scaledWidth, top + scaledHeight)
    }

    // Возвращает координаты в системе изображения (0..intrinsicSize), если точка внутри rect, иначе null
    fun screenToImage(pos: Offset, rect: Rect, intrinsicSize: Size): Offset? {
        if (!rect.contains(pos)) return null
        val x = (pos.x - rect.left) / rect.width * intrinsicSize.width
        val y = (pos.y - rect.top) / rect.height * intrinsicSize.height
        return Offset(x, y)
    }

    // Принудительно ограничивает координаты границами изображения (clamp)
    fun screenToImageClamped(pos: Offset, rect: Rect, intrinsicSize: Size): Offset {
        val x = ((pos.x - rect.left) / rect.width * intrinsicSize.width)
            .coerceIn(0f, intrinsicSize.width)
        val y = ((pos.y - rect.top) / rect.height * intrinsicSize.height)
            .coerceIn(0f, intrinsicSize.height)
        return Offset(x, y)
    }

    fun imageToScreen(pos: Offset, rect: Rect, intrinsicSize: Size): Offset {
        return Offset(
            x = rect.left + pos.x / intrinsicSize.width * rect.width,
            y = rect.top + pos.y / intrinsicSize.height * rect.height
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { },
                actions = {
                    IconButton(onClick = vm::undo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = vm::clear) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                    IconButton(onClick = { launcher.launch("image/*") }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = "Open Image")
                    }
                    IconButton(onClick = {
                        val bitmap = vm.exportBitmap(context)
                        if (bitmap != null) {
                            vm.saveToGalleryAsync(
                                context, bitmap,
                                settingsVm.saveUri, settingsVm.fileNamePattern
                            ) { uri ->
                                if (uri != null) {
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = {
                        val bitmap = vm.exportBitmap(context)
                        if (bitmap != null) {
                            vm.shareBitmap(context, bitmap)
                        } else {
                            Toast.makeText(context, "Nothing to share", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = UIColor,
                    navigationIconContentColor = UIColor,
                    actionIconContentColor = UIColor
                )
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = Color.Black) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = if (color == vm.currentColor) 3.dp else 2.dp,
                                        color = if (color == vm.currentColor) Color.White else Color.Gray,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(color)
                                    .clickable { vm.currentColor = color }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = vm.brushSize,
                        onValueChange = { vm.brushSize = it },
                        valueRange = 2f..60f,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { containerSize = it.size }
            )

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // Drag – рисование линий с остановкой на границе и продолжением после возврата
                    .pointerInput(vm.imageUri, displayImageRect, intrinsicSize) {
                        if (vm.imageUri == null || displayImageRect == null || intrinsicSize == Size.Unspecified)
                            return@pointerInput

                        var wasInside = false

                        detectDragGestures(
                            onDragStart = { screenPos ->
                                val imagePos = screenToImage(screenPos, displayImageRect, intrinsicSize)
                                if (imagePos != null) {
                                    vm.startPath(
                                        Offset(imagePos.x / intrinsicSize.width, imagePos.y / intrinsicSize.height)
                                    )
                                    wasInside = true
                                } else {
                                    wasInside = false
                                }
                            },
                            onDrag = { change, _ ->
                                val insidePoint = screenToImage(change.position, displayImageRect, intrinsicSize)
                                val inside = insidePoint != null

                                if (inside && !wasInside) {
                                    vm.addPoint(
                                        Offset(insidePoint.x / intrinsicSize.width, insidePoint.y / intrinsicSize.height)
                                    )
                                } else if (!inside && wasInside) {
                                    val clamped = screenToImageClamped(change.position, displayImageRect, intrinsicSize)
                                    vm.addPoint(
                                        Offset(clamped.x / intrinsicSize.width, clamped.y / intrinsicSize.height)
                                    )
                                } else if (inside) {
                                    // Обычное движение внутри
                                    vm.addPoint(
                                        Offset(insidePoint.x / intrinsicSize.width, insidePoint.y / intrinsicSize.height)
                                    )
                                }

                                wasInside = inside
                            },
                            onDragEnd = {
                                vm.endPath()
                            }
                        )
                    }
                    // Tap – одиночная точка (только внутри изображения)
                    .pointerInput(vm.imageUri, displayImageRect, intrinsicSize) {
                        if (vm.imageUri == null || displayImageRect == null || intrinsicSize == Size.Unspecified)
                            return@pointerInput

                        detectTapGestures { screenPos ->
                            val imagePos = screenToImage(screenPos, displayImageRect, intrinsicSize)
                            if (imagePos != null) {
                                vm.addTapPoint(
                                    Offset(imagePos.x / intrinsicSize.width, imagePos.y / intrinsicSize.height)
                                )
                            }
                        }
                    }
            ) {
                val rect = displayImageRect ?: return@Canvas
                val currentScale = if (vm.displayScale > 0f) vm.displayScale else 1f

                // Сохранённые штрихи
                vm.strokes.forEach { stroke ->
                    val baseWidth = if (stroke.displayScale > 0f) stroke.width / stroke.displayScale else stroke.width
                    val screenWidth = baseWidth * currentScale

                    if (stroke.isDot && stroke.points.size == 1) {
                        val pImage = Offset(
                            stroke.points[0].x * intrinsicSize.width,
                            stroke.points[0].y * intrinsicSize.height
                        )
                        val center = imageToScreen(pImage, rect, intrinsicSize)
                        drawCircle(
                            color = stroke.color,
                            radius = screenWidth / 2f,
                            center = center,
                            style = Fill
                        )
                    } else if (stroke.points.size >= 2) {
                        val path = Path().apply {
                            stroke.points.forEachIndexed { index, pNorm ->
                                val pImage = Offset(pNorm.x * intrinsicSize.width, pNorm.y * intrinsicSize.height)
                                val screenP = imageToScreen(pImage, rect, intrinsicSize)
                                if (index == 0) moveTo(screenP.x, screenP.y)
                                else lineTo(screenP.x, screenP.y)
                            }
                        }
                        drawPath(path, stroke.color, style = Stroke(width = screenWidth))
                    }
                }

                // Текущий рисуемый путь
                if (vm.currentPath.isNotEmpty()) {
                    val baseWidth = if (currentScale > 0f) vm.brushSize / currentScale else vm.brushSize
                    val screenWidth = baseWidth * currentScale

                    if (vm.currentPath.size == 1) {
                        val pImage = Offset(
                            vm.currentPath[0].x * intrinsicSize.width,
                            vm.currentPath[0].y * intrinsicSize.height
                        )
                        val center = imageToScreen(pImage, rect, intrinsicSize)
                        drawCircle(
                            color = vm.currentColor,
                            radius = screenWidth / 2f,
                            center = center,
                            style = Fill
                        )
                    } else {
                        val path = Path().apply {
                            vm.currentPath.forEachIndexed { index, pNorm ->
                                val pImage = Offset(pNorm.x * intrinsicSize.width, pNorm.y * intrinsicSize.height)
                                val screenP = imageToScreen(pImage, rect, intrinsicSize)
                                if (index == 0) moveTo(screenP.x, screenP.y)
                                else lineTo(screenP.x, screenP.y)
                            }
                        }
                        drawPath(path, vm.currentColor, style = Stroke(width = screenWidth))
                    }
                }
            }

            if (vm.imageUri == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select an image", color = Color.Gray)
                }
            }
        }
    }
}