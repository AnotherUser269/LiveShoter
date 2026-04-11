package com.example.liveshoter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.liveshoter.ui.navigation.AppNavHost
import com.example.liveshoter.ui.theme.LiveShoterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveShoterTheme {
                val navController = rememberNavController()

                AppNavHost(navController = navController)
            }
        }
    }
}
