package com.example.liveshoter.ui.navigation

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.liveshoter.R
import com.example.liveshoter.ui.screens.dynamicEditorScreen.DynamicEditorScreen
import com.example.liveshoter.ui.screens.mainScreen.MainScreen
import com.example.liveshoter.ui.screens.settingsScreen.SettingsScreen

import com.example.liveshoter.ui.screens.staticEditorScreen.StaticEditorScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    // Время анимации при переходе между экранами
    val animationMills = 150

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { fadeIn(animationSpec = tween(animationMills)) },
        exitTransition = { fadeOut(animationSpec = tween(animationMills)) },
        modifier = Modifier.background(Color.Black)
    ) {
        // Главное меню
        composable(Screen.Home.route) {
            MainScreen(navController)
        }

        // Статический редактор
        composable(Screen.StaticEditor.route) {
            StaticEditorScreen(navController)
        }

        // Динамический редактор
        composable(Screen.DynamicEditor.route) {
            DynamicEditorScreen(navController)
        }

        // Настройки
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }

        //
    }
}