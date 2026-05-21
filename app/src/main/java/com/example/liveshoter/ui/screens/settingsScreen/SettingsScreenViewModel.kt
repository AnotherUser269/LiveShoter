package com.example.liveshoter.ui.screens.settingsScreen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel

class SettingsScreenViewModel(context: Context) : ViewModel() {

    var fileNamePattern by mutableStateOf("screenshot_{time}")
    var saveUri by mutableStateOf<String?>(null)
    var savePathDisplayName by mutableStateOf<String?>(null)
    var fps by mutableIntStateOf(8)   // частота кадров для DynamicEditor

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        fileNamePattern = prefs.getString("file_name_pattern", fileNamePattern)!!
        saveUri = prefs.getString("save_uri", null)
        savePathDisplayName = prefs.getString("save_display_name", null)
        fps = prefs.getInt("fps", 8)
    }

    fun updateFileNamePattern(v: String) {
        fileNamePattern = v
        prefs.edit { putString("file_name_pattern", v) }
    }

    fun updateSaveUri(uri: String) {
        saveUri = uri
        prefs.edit { putString("save_uri", uri) }
    }

    fun updateSavePathDisplayName(name: String) {
        savePathDisplayName = name
        prefs.edit { putString("save_display_name", name) }
    }

    fun updateFps(value: Int) {
        fps = value
        prefs.edit { putInt("fps", value) }
    }

    fun resetDefaults() {
        updateFileNamePattern("screenshot_{time}")
        updateSaveUri("Pictures")
        updateSavePathDisplayName("Pictures")
        updateFps(8)
    }

    fun savePreferences() {
        prefs.edit {
            putString("file_name_pattern", fileNamePattern)
            putString("save_uri", saveUri)
            putString("save_display_name", savePathDisplayName)
            putInt("fps", fps)
        }
    }
}