package com.francotte.homecontroller

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.francotte.homecontroller.core.designsystem.theme.HomeControllerTheme
import com.francotte.homecontroller.navigation.HomeControllerAppShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() doit etre appele AVANT super.onCreate().
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Duree minimale d'affichage du splash, sans bloquer le thread principal :
        // la condition est reevaluee a chaque frame et rend la main des que le delai est ecoule.
        val splashStartUptime = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStartUptime < SPLASH_MIN_DURATION_MS
        }
        // Sortie du splash : fondu + leger zoom, identique sur toutes les versions.
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(300L)
                .withEndAction { provider.remove() }
                .start()
        }
        setContent {
            HomeControllerTheme {
                HomeControllerAppShell()
            }
        }
    }

    private companion object {
        const val SPLASH_MIN_DURATION_MS = 1000L
    }
}
