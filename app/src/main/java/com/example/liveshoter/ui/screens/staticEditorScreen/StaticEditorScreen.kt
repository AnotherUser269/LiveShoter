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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
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

    // Settings ViewModel (для чтения saveUri и fileNamePattern)
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

    // Лаунчер для выбора изображения
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        vm.setImage(uri)
    }

    val painter = rememberAsyncImagePainter(vm.imageUri)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Внутренний размер изображения (intrinsic size)
    val intrinsicSize: Size = when (val state = painter.state) {
        is coil.compose.AsyncImagePainter.State.Success -> state.painter.intrinsicSize
        else -> Size.Unspecified
    }

    // Вычисление прямоугольника, в котором будет отображаться изображение
    val displayImageRect: Rect? = remember(containerSize, intrinsicSize) {
        if (containerSize == IntSize.Zero || intrinsicSize == Size.Unspecified) return@remember null
        if (intrinsicSize.width <= 0f || intrinsicSize.height <= 0f) return@remember null

        val scale = min(
            containerSize.width.toFloat() / intrinsicSize.width,
            containerSize.height.toFloat() / intrinsicSize.height
        )
        val scaledWidth = intrinsicSize.width * scale
        val scaledHeight = intrinsicSize.height * scale
        val left = (containerSize.width - scaledWidth) / 2f
        val top = (containerSize.height - scaledHeight) / 2f
        Rect(left, top, left + scaledWidth, top + scaledHeight)
    }

    // Преобразование экранных координат в координаты изображения
    fun screenToImage(pos: Offset, rect: Rect, intrinsicSize: Size): Offset? {
        if (!rect.contains(pos)) return null
        return Offset(
            x = (pos.x - rect.left) / rect.width * intrinsicSize.width,
            y = (pos.y - rect.top) / rect.height * intrinsicSize.height
        )
    }

    // Преобразование координат изображения в экранные
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
                    IconButton(onClick = vm::undo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Очистка
                        IconButton(onClick = vm::clear) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                        // Открыть изображение
                        IconButton(onClick = { launcher.launch("image/*") }) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Open Image")
                        }
                        // Сохранить — теперь учитываем настройки (saveUri и fileNamePattern)
                        IconButton(onClick = {
                            val bitmap = vm.exportBitmap(context, intrinsicSize)
                            if (bitmap != null) {
                                vm.saveToGalleryAsync(
                                    context,
                                    bitmap,
                                    settingsVm.saveUri,
                                    settingsVm.fileNamePattern
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Выбор цвета
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(2.dp, Color.Gray)
                                    .background(color)
                                    .clickable { vm.currentColor = color }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Выбор размера кисти
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

            // Отображение изображения
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { containerSize = it.size }
            )

            // Холст для рисования поверх изображения
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(vm.imageUri, displayImageRect, intrinsicSize) {
                        if (vm.imageUri == null || displayImageRect == null || intrinsicSize == Size.Unspecified) return@pointerInput

                        detectDragGestures(
                            onDragStart = { screenPos ->
                                val imagePos = screenToImage(screenPos, displayImageRect, intrinsicSize)
                                imagePos?.let { vm.startPath(Offset(it.x / intrinsicSize.width, it.y / intrinsicSize.height)) }
                            },
                            onDrag = { change, _ ->
                                val imagePos = screenToImage(change.position, displayImageRect, intrinsicSize)
                                imagePos?.let { vm.addPoint(Offset(it.x / intrinsicSize.width, it.y / intrinsicSize.height)) }
                            },
                            onDragEnd = { vm.endPath() }
                        )
                    }
            ) {
                val rect = displayImageRect ?: return@Canvas

                // Рисуем сохранённые штрихи
                vm.strokes.forEach { stroke ->
                    val path = Path().apply {
                        stroke.points.forEachIndexed { index, pNorm ->
                            val pImage = Offset(pNorm.x * intrinsicSize.width, pNorm.y * intrinsicSize.height)
                            val screenP = imageToScreen(pImage, rect, intrinsicSize)
                            if (index == 0) moveTo(screenP.x, screenP.y)
                            else lineTo(screenP.x, screenP.y)
                        }
                    }
                    drawPath(path, stroke.color, style = Stroke(width = stroke.width))
                }

                // Рисуем текущий путь
                if (vm.currentPath.isNotEmpty()) {
                    val path = Path().apply {
                        vm.currentPath.forEachIndexed { index, pNorm ->
                            val pImage = Offset(pNorm.x * intrinsicSize.width, pNorm.y * intrinsicSize.height)
                            val screenP = imageToScreen(pImage, rect, intrinsicSize)
                            if (index == 0) moveTo(screenP.x, screenP.y)
                            else lineTo(screenP.x, screenP.y)
                        }
                    }
                    drawPath(path, vm.currentColor, style = Stroke(width = vm.brushSize))
                }
            }

            // Пустое состояние
            if (vm.imageUri == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select an image", color = Color.Gray)
                }
            }
        }
    }
}