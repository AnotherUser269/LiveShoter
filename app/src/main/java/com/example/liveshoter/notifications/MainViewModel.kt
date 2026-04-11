package com.example.liveshoter.notifications

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel: ViewModel() {
    fun minimizeAndNotify(activity: Activity) {
        // Создать канал и показать уведомление
        NotificationHelper.createChannel(activity.applicationContext)
        NotificationHelper.showNotification(activity.applicationContext)

        // Сворачиваем приложение (переводим в фон)
        viewModelScope.launch(Dispatchers.Main) {
            activity.moveTaskToBack(true)
        }
    }
}
