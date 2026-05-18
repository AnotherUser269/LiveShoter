package com.example.liveshoter.ui.screens.settingsScreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.provider.DocumentsContract
import java.net.URLDecoder
import androidx.core.content.edit
import androidx.core.net.toUri

class SettingsScreenViewModel(private val context: Context) : ViewModel() {

    var fileNamePattern by mutableStateOf("screenshot_{time}")
        private set

    var saveUri by mutableStateOf<String?>(null)
        private set

    var savePathDisplayName by mutableStateOf<String?>(null)
        private set

    private val prefs by lazy { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    init {
        // Загрузка сохранённых настроек при старте
        fileNamePattern = prefs.getString("file_name_pattern", fileNamePattern) ?: fileNamePattern
        saveUri = prefs.getString("save_uri", null)
        savePathDisplayName = prefs.getString("save_display_name", null)

        // Если displayName отсутствует, попытаться восстановить из uri
        saveUri?.let { uriString ->
            if (savePathDisplayName.isNullOrEmpty()) {
                try {
                    val uri = uriString.toUri()
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val afterColon = docId.substringAfter(':', docId)
                    savePathDisplayName = URLDecoder.decode(afterColon, "UTF-8")
                } catch (_: Exception) {  }
            }
        }
    }

    fun updateFileNamePattern(v: String) {
        fileNamePattern = v
        prefs.edit { putString("file_name_pattern", v) }
    }

    fun updateSaveUri(uriString: String) {
        saveUri = uriString
        prefs.edit { putString("save_uri", uriString) }
    }

    fun updateSavePathDisplayName(name: String) {
        savePathDisplayName = name
        prefs.edit { putString("save_display_name", name) }
    }

    fun resetDefaults() {
        updateFileNamePattern("screenshot_{time}")
        updateSaveUri("Pictures")
        updateSavePathDisplayName("Pictures")
    }

    fun savePreferences() {
        prefs.edit {
            putString("file_name_pattern", fileNamePattern)
            putString("save_uri", saveUri)
            putString("save_display_name", savePathDisplayName)
        }
    }
}