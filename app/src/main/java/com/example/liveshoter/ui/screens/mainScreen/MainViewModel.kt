package com.example.liveshoter.ui.screens.mainScreen

import android.app.Activity
import android.util.Log
import android.util.MutableBoolean
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.serialization.serializers.MutableStateFlowSerializer
import com.example.liveshoter.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    fun onOpenSettings() {
        Log.d("MainScreen", "Opened Settings")
        viewModelScope.launch { _events.emit(UiEvent.OpenSettings) }
    }

    fun onStartCapturing(activity: Activity) {
        Log.d("MainScreen", "Starting capturing")        // Создаём канал и показываем уведомление из пакета
        NotificationHelper.createChannel(activity.applicationContext)
        NotificationHelper.showNotification(activity.applicationContext)        // Сворачиваем приложение
        activity.moveTaskToBack(true)
    }

    fun onOpenStaticEditor() {
        Log.d("MainScreen", "Opened StaticEditor")
        viewModelScope.launch { _events.emit(UiEvent.OpenStaticEditor) }
    }

    fun onOpenDynamicEditor() {
        Log.d("MainScreen", "Opened DynamicEditor")
        viewModelScope.launch { _events.emit(UiEvent.OpenDynamicEditor) }
    }

    fun onStartCapturing() {
        Log.d("MainScreen", "Starting capturing")

        TODO()
    }


    // Popup инструкции
    private val _instructionPopupShown = MutableStateFlow(false)
    val instructionPopupShown = _instructionPopupShown.asStateFlow()

    fun onOpenInstructionsSection() {
        Log.d("MainScreen", "Opened instructions section")

        _instructionPopupShown.value = true
    }

    fun dismissInstructionPopup() {
        _instructionPopupShown.value = false
    }

    // Popup about
    private val _aboutPopupShown = MutableStateFlow(false)
    val aboutPopupShown = _aboutPopupShown.asStateFlow()

    fun onOpenAboutSection() {
        Log.d("MainScreen", "Opened about section")

        _aboutPopupShown.value = true
    }

    fun dismissAboutPopup() {
        _aboutPopupShown.value = false
    }

}

sealed class UiEvent {
    object OpenSettings : UiEvent()
    object OpenStaticEditor : UiEvent()
    object OpenDynamicEditor : UiEvent()
}
