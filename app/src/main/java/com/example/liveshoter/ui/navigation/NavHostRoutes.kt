package com.example.liveshoter.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("Home")
    object StaticEditor : Screen("StaticEditor")
    object DynamicEditor : Screen("DynamicEditor")
    object Settings : Screen("Settings")

}