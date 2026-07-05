package com.francotte.homecontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import com.francotte.homecontroller.presentation.scan.ScanScreen
import com.francotte.homecontroller.ui.theme.HomeControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeControllerTheme {
                ScanScreen(modifier = Modifier)
            }
        }
    }
}
