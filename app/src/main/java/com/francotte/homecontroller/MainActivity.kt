package com.francotte.homecontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.francotte.homecontroller.navigation.HomeControllerNavDisplay
import com.francotte.homecontroller.ui.theme.HomeControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeControllerTheme {
                HomeControllerNavDisplay()
            }
        }
    }
}
