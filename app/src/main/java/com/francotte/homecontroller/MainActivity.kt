package com.francotte.homecontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.francotte.homecontroller.core.designsystem.theme.HomeControllerTheme
import com.francotte.homecontroller.navigation.HomeControllerAppShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeControllerTheme {
                HomeControllerAppShell()
            }
        }
    }
}
