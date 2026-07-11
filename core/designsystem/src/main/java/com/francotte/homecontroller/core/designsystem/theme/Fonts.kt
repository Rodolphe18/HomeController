package com.francotte.homecontroller.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.francotte.homecontroller.core.designsystem.R

/**
 * Lora — elegant reading serif used for long-form body text (e.g. recipe instructions).
 * Bundled fonts (res/font), so it renders offline with no fallback flash.
 */
val Lora =
    FontFamily(
        Font(R.font.lora_regular, FontWeight.Normal),
        Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.lora_medium, FontWeight.Medium),
        Font(R.font.lora_semibold, FontWeight.SemiBold),
    )

/**
 * Playfair Display — high-contrast display serif used for titles and section headers
 * (recipe name, "Ingredients", "Instructions"). Pairs with [Lora] for the editorial
 * "cookbook" look. Bundled fonts, so titles render offline with no fallback flash.
 */
val Playfair =
    FontFamily(
        Font(R.font.playfair_regular, FontWeight.Normal),
        Font(R.font.playfair_medium, FontWeight.Medium),
        Font(R.font.playfair_semibold, FontWeight.SemiBold),
        Font(R.font.playfair_bold, FontWeight.Bold),
    )
