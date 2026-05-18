package com.example.liveshoter.ui.screens.settingsScreen

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import com.example.liveshoter.ui.theme.uiColor as UIColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current

    val vm: SettingsScreenViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsScreenViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsScreenViewModel(context) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Settings",
                    color = UIColor,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center) },
            colors = TopAppBarColors(
                containerColor = Color.Black,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = UIColor,
                titleContentColor = UIColor,
                actionIconContentColor = UIColor,
            ),)

        },

        bottomBar = {
            BottomAppBar(
                containerColor = Color.Black,
                contentColor = UIColor
            ) {
                // Кнопки Reset, Save, Back
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                        Text("Back", color = Color.White)
                    }

                    Button(onClick = { vm.resetDefaults() }, modifier = Modifier.weight(1f)) {
                        Text("Reset", color = Color.White)
                    }

                    Button(onClick = { vm.savePreferences(); navController.popBackStack() }, modifier = Modifier.weight(1f)) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File Name Pattern
                item {
                    Column {
                        Text("File Name Pattern", color = UIColor)
                        var editFileName by remember { mutableStateOf(vm.fileNamePattern) }
                        BasicTextField(
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            value = editFileName,
                            onValueChange = {
                                editFileName = it
                                vm.updateFileNamePattern(it)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        )
                    }
                }

                // Save Path с выбором директории
                item {
                    Column {
                        Text("Save Path", color = UIColor)

                        val launcher = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree()
                        ) { uri: Uri? ->
                            uri?.let {
                                vm.updateSaveUri(it.toString())
                                try {
                                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    context.contentResolver.takePersistableUriPermission(it, flags)
                                } catch (_: Exception) {}

                                val docId = DocumentsContract.getTreeDocumentId(it)
                                val afterColon = docId.substringAfter(':', docId)
                                val decoded = URLDecoder.decode(afterColon, "UTF-8")
                                vm.updateSavePathDisplayName(decoded)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pathValue by remember { derivedStateOf { vm.savePathDisplayName ?: vm.saveUri ?: "" } }

                            BasicTextField(
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                value = pathValue,
                                onValueChange = { /* display only */ },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Button(onClick = { launcher.launch(null) }) {
                                Text("Select Folder", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}