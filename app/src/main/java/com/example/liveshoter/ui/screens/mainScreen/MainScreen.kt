package com.example.liveshoter.ui.screens.mainScreen

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.liveshoter.MainActivity
import com.example.liveshoter.R
import com.example.liveshoter.capture.ProjectionHolder
import com.example.liveshoter.notifications.NotificationHelper
import com.example.liveshoter.ui.navigation.Screen
import com.example.liveshoter.ui.theme.uiColor as UIColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController, viewModel: MainViewModel = viewModel()) {
    val instructionPopupShown by viewModel.instructionPopupShown.collectAsState()
    val aboutPopupShown by viewModel.aboutPopupShown.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                UiEvent.OpenSettings -> navController.navigate(Screen.Settings.route)
                UiEvent.OpenStaticEditor -> navController.navigate(Screen.StaticEditor.route)
                UiEvent.OpenDynamicEditor -> navController.navigate(Screen.DynamicEditor.route)
                UiEvent.StartCapturing -> {
                    if (ProjectionHolder.hasSavedPermission()) {
                        NotificationHelper.showActionNotification(context)
                        (context as Activity).moveTaskToBack(true)
                    } else {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("request_projection", true)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarColors(
                    containerColor = Color.Black,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = UIColor,
                    titleContentColor = UIColor,
                    actionIconContentColor = UIColor,
                ),
                title = {
                    Text(
                        text = "LiveShoter",
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onOpenSettings() }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Gear Icon",
                        )
                    }
                },
                actions = { GetDropDown(viewModel) }
            )
        },
        bottomBar = {
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
            Button(
                onClick = { viewModel.onStartCapturing() },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Start Capturing", color = Color.White)
            }
            Button(
                onClick = { viewModel.onOpenStaticEditor() },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Static Editor", color = Color.White)
            }
            Button(
                onClick = { viewModel.onOpenDynamicEditor() },
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Dynamic Editor", color = Color.White)
            }
        }
    }

    if (instructionPopupShown) GetPopupInstructions(viewModel)
    if (aboutPopupShown) GetAboutInstructions(viewModel)
}

@Composable
fun GetDropDown(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu Icon")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Instructions") },
            onClick = { viewModel.onOpenInstructionsSection(); expanded = false }
        )
        DropdownMenuItem(
            text = { Text("About") },
            onClick = { viewModel.onOpenAboutSection(); expanded = false }
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
        confirmButton = { Button(onClick = { viewModel.dismissInstructionPopup() }) { Text("Understood!", color = Color.White) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetAboutInstructions(viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissAboutPopup() },
        title = { Text("About") },
        text = { Text(LocalContext.current.getString(R.string.about)) },
        confirmButton = { Button(onClick = { viewModel.dismissAboutPopup() }) { Text("OK", color = Color.White) } }
    )
}