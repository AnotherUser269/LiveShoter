package com.example.liveshoter.ui.screens.mainScreen

import android.annotation.SuppressLint
import android.app.Activity
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.liveshoter.ui.theme.uiColor as UIColor
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.liveshoter.R
import com.example.liveshoter.ui.navigation.Screen
import com.example.liveshoter.notifications.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController, viewModel: MainViewModel = viewModel()) {
    val instructionPopupShown by viewModel.instructionPopupShown.collectAsState()
    val aboutPopupShown by viewModel.aboutPopupShown.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect {
            event -> when (event) {
                UiEvent.OpenSettings -> navController.navigate(Screen.Settings.route)
                UiEvent.OpenStaticEditor -> navController.navigate(Screen.StaticEditor.route)
                UiEvent.OpenDynamicEditor -> navController.navigate(Screen.DynamicEditor.route)
            }
        }
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                // Отвечает за кнопки меню, настроек и название приложения
                colors = TopAppBarColors(
                    containerColor = Color.Black,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = UIColor,
                    titleContentColor = UIColor,
                    actionIconContentColor = UIColor,
                ),

                title = { Text(
                    text = "LiveShoter",
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                ) },

                // Переход к экрану настроек
                navigationIcon = {
                    IconButton(onClick = { viewModel.onOpenSettings() }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Gear Icon",
                        )
                    }
                },

                // Выводит выпадающее меню
                actions = {
                    GetDropDown(viewModel)
                }
            )
        },

        bottomBar = {
            // Версия приложения
            BottomAppBar(
                containerColor = Color.Black,
                contentColor = UIColor
            ) {
                Text(
                    text = LocalContext.current.getString(R.string.version),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Кнопка для активации приложения
            Button(
                onClick = {
                    val activity = when (val ctx = context) {
                        is Activity -> ctx
                        is android.content.ContextWrapper -> {
                            var base = ctx.baseContext
                            while (base is android.content.ContextWrapper && base !is Activity) base = base.baseContext
                            base as? Activity
                        }
                        else -> null
                    }
                    activity?.let { viewModel.onStartCapturing(it) }
                },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Start Capturing", color = Color.White)
            }


            // Кнопка для перехода в редактор, который вернет статическое изображение
            Button(
                onClick = { viewModel.onOpenStaticEditor() },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Static Editor", color = Color.White)
            }

            // Кнопка перехода в редактор, который вернет файл анимации
            Button(
                onClick = { viewModel.onOpenDynamicEditor() },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Dynamic Editor", color = Color.White)
            }
        }
    }

    // Показ инструкций
    if (instructionPopupShown) GetPopupInstructions(viewModel)

    // Показ about
    if (aboutPopupShown) GetAboutInstructions(viewModel)
}

@Composable
fun GetDropDown(viewModel: MainViewModel) {
    // Рисует выпадающий список
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "Menu Icon",
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Instructions") },
            onClick = { viewModel.onOpenInstructionsSection(); expanded = false}
        )
        DropdownMenuItem(
            text = { Text("About") },
            onClick = { viewModel.onOpenAboutSection(); expanded = false}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetPopupInstructions(viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissInstructionPopup() },
        title = { Text("Instructions") },
        text = { Text(LocalContext.current.getString(R.string.instructions)) },
        confirmButton = { Button(onClick = { viewModel.dismissInstructionPopup() }) { Text("Understood!") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetAboutInstructions(viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissAboutPopup() },
        title = { Text("About") },
        text = { Text(LocalContext.current.getString(R.string.about)) },
        confirmButton = { Button(onClick = { viewModel.dismissAboutPopup() }) { Text("OK") } }
    )
}
