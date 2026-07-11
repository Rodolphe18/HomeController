package com.francotte.homecontroller.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

// Set of Material typography styles to start with
val Typography =
    Typography(
        // Titles → Playfair
        displayLarge = default.displayLarge.copy(fontFamily = Playfair),
        displayMedium = default.displayMedium.copy(fontFamily = Playfair),
        displaySmall = default.displaySmall.copy(fontFamily = Playfair),
        headlineLarge = default.headlineLarge.copy(fontFamily = Playfair),
        headlineMedium = default.headlineMedium.copy(fontFamily = Playfair),
        headlineSmall = default.headlineSmall.copy(fontFamily = Playfair),
        titleLarge = default.titleLarge.copy(fontFamily = Playfair),
        titleMedium = default.titleMedium.copy(fontFamily = Playfair),
        titleSmall = default.titleSmall.copy(fontFamily = Playfair),
        // Body & labels → Lora
        bodyLarge = default.bodyLarge.copy(fontFamily = Lora),
        bodyMedium = default.bodyMedium.copy(fontFamily = Lora),
        bodySmall = default.bodySmall.copy(fontFamily = Lora),
        labelLarge = default.labelLarge.copy(fontFamily = Lora),
        labelMedium = default.labelMedium.copy(fontFamily = Lora),
        labelSmall = default.labelSmall.copy(fontFamily = Lora),
    )
