package com.francotte.homecontroller.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.francotte.homecontroller.core.designsystem.R

/** Figtree — sans moderne et chaleureux, pour tout le corps d'UI (labels, body, titres courants). */
val Figtree =
    FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semi_bold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )

/** Bricolage Grotesque — display à caractère, réservée aux grands titres et écrans d'état. */
val Bricolage =
    FontFamily(
        Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
        Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
        Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    )
