package com.francotte.homecontroller.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val default = Typography()

/** Display/headline → Bricolage (caractère) ; titres/corps/labels → Figtree (sans UI). */
val Typography =
    Typography(
        displayLarge = default.displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        displayMedium = default.displayMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        displaySmall = default.displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        headlineLarge = default.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        headlineMedium = default.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Medium),
        headlineSmall = default.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Medium),
        titleLarge = default.titleLarge.copy(fontFamily = Figtree),
        titleMedium = default.titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        titleSmall = default.titleSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        bodyLarge = default.bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = default.bodyMedium.copy(fontFamily = Figtree),
        bodySmall = default.bodySmall.copy(fontFamily = Figtree),
        labelLarge = default.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelMedium = default.labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
        labelSmall = default.labelSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
    )
