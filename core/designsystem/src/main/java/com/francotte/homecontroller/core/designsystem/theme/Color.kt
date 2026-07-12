package com.francotte.homecontroller.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette « Indigo Électrique ».
 *
 * Deux niveaux de lecture :
 *  1. La PALETTE NOMMÉE ci-dessous — chaque teinte réelle, avec un nom parlant.
 *  2. Les RÔLES MATERIAL (tout en bas) — quel rôle du thème utilise quelle teinte.
 *
 * Beaucoup de teintes servent à la fois en clair et en sombre (ex. un conteneur clair
 * devient le texte sur ce conteneur en sombre) : elles sont donc déclarées une seule
 * fois et réutilisées, ce qui rend les correspondances visibles.
 */

// ---- Indigo (bleu électrique) — famille primaire ----
val IndigoElectric = Color(0xFF3B5BF0) // bleu-indigo vif : primaire (clair) / inversePrimary (sombre)
val IndigoLight = Color(0xFFB9C3FF)    // bleu clair : primaire (sombre) / inversePrimary (clair)
val IndigoMid = Color(0xFF2340D0)      // bleu moyen : conteneur primaire (sombre)
val IndigoDeep = Color(0xFF00218C)     // bleu profond : texte sur primaire (sombre)
val IndigoNight = Color(0xFF001159)    // bleu nuit : texte sur conteneur primaire (clair)
val IndigoPale = Color(0xFFDDE1FF)     // bleu pâle : conteneur primaire (clair) / texte sur conteneur (sombre)

// ---- Cyan — famille secondaire ----
val CyanDeep = Color(0xFF00727E)  // cyan profond : secondaire (clair)
val CyanLight = Color(0xFF83D3DE) // cyan clair : secondaire (sombre)
val CyanMid = Color(0xFF004E57)   // cyan moyen : conteneur secondaire (sombre)
val CyanDark = Color(0xFF00363D)  // cyan très sombre : texte sur secondaire (sombre)
val CyanNight = Color(0xFF002B31) // cyan nuit : texte sur conteneur secondaire (clair)
val CyanPale = Color(0xFFB0ECF4)  // cyan pâle : conteneur secondaire (clair) / texte sur conteneur (sombre)

// ---- Magenta — famille tertiaire ----
val MagentaVivid = Color(0xFFB21E63) // magenta vif : tertiaire (clair)
val MagentaLight = Color(0xFFFFB0CE) // rose clair : tertiaire (sombre)
val MagentaMid = Color(0xFF8E1554)   // magenta moyen : conteneur tertiaire (sombre)
val MagentaDeep = Color(0xFF650036)  // magenta profond : texte sur tertiaire (sombre)
val MagentaNight = Color(0xFF3E0021) // magenta nuit : texte sur conteneur tertiaire (clair)
val MagentaPale = Color(0xFFFFD9E5)  // magenta pâle : conteneur tertiaire (clair) / texte sur conteneur (sombre)

// ---- Rouge — famille erreur ----
val RedError = Color(0xFFBA1A1A) // rouge erreur : erreur (clair)
val RedLight = Color(0xFFFFB4AB) // rouge clair : erreur (sombre)
val RedMid = Color(0xFF93000A)   // rouge moyen : conteneur erreur (sombre)
val RedDeep = Color(0xFF690005)  // rouge profond : texte sur erreur (sombre)
val RedNight = Color(0xFF410002) // rouge nuit : texte sur conteneur erreur (clair)
val RedPale = Color(0xFFFFDAD6)  // rouge pâle : conteneur erreur (clair) / texte sur conteneur (sombre)

// ---- Neutres (teintés indigo) — fonds, surfaces, textes ----
val White = Color(0xFFFFFFFF)          // blanc : textes sur couleurs vives (clair) / surface la plus basse (clair)
val Black = Color(0xFF000000)          // noir : scrim (ombrage modal)
val InkIndigo = Color(0xFF1B1830)      // encre : texte principal (clair) / surfaceContainerLow (sombre)
val MistLight = Color(0xFFE5E0F4)      // brume claire : texte principal (sombre) / inverseSurface (sombre)
val SlateIndigo = Color(0xFF302D44)    // ardoise : surface inversée (clair) / texte sur surface inversée (sombre)
val MistWhite = Color(0xFFF3EEFC)      // brume blanche : texte sur surface inversée (clair)

// Surfaces claires (du plus clair au plus soutenu)
val LavenderWhite = Color(0xFFFBF8FF) // fond & surface (clair)
val LavenderMist = Color(0xFFF5F1FD)  // surfaceContainerLow (clair)
val LavenderHaze = Color(0xFFF1ECFB)  // surfaceContainer (clair)
val LavenderTint = Color(0xFFEBE4F7)  // surfaceContainerHigh (clair)
val LavenderDusk = Color(0xFFE5DFF2)  // surfaceContainerHighest (clair)
val LavenderDim = Color(0xFFDCD8E8)   // surfaceDim (clair)

// Surfaces sombres (du plus sombre au plus clair) — recette « dark élevé » adoucie :
// toute la pile est relevée et un soupçon désaturée (charbon-indigo, moins violet) pour un rendu plus doux.
val IndigoInk = Color(0xFF16121F)     // surfaceContainerLowest (sombre)
val IndigoBlack = Color(0xFF211C3A)   // fond & surface & surfaceDim (sombre) — adouci
val IndigoNightSurface = Color(0xFF2A2540) // surfaceContainer (sombre)

// Dégradé de fond très subtil (sombre) : du haut légèrement plus clair vers le fond de base.
val IndigoBackdropTop = Color(0xFF2A2448)    // haut : indigo nuit remonté (fin liseré plus clair)
val IndigoBackdropBottom = Color(0xFF211C3A) // bas : fond de base (= IndigoBlack)
val IndigoSlate = Color(0xFF332D4A)   // surfaceContainerHigh (sombre) — cartes
val IndigoSlateHigh = Color(0xFF3D3757) // surfaceContainerHighest (sombre)
val IndigoBright = Color(0xFF423B5C)  // surfaceBright (sombre)

// Neutres-variantes (contours, surfaceVariant)
val NeutralVariantLight = Color(0xFFE5E0F0) // surfaceVariant (clair)
val NeutralVariant = Color(0xFF47455A)      // texte secondaire (clair) / surfaceVariant & outlineVariant (sombre)
val OutlineFaint = Color(0xFFC9C4DA)        // contour discret (clair) / texte secondaire (sombre)
val OutlineGreyLight = Color(0xFF78758B)    // contour (clair)
val OutlineGreyDark = Color(0xFF928FA4)     // contour (sombre)

// ==================== RÔLES MATERIAL ====================

// ---- Clair ----
val primaryLight = IndigoElectric
val onPrimaryLight = White
val primaryContainerLight = IndigoPale
val onPrimaryContainerLight = IndigoNight
val secondaryLight = CyanDeep
val onSecondaryLight = White
val secondaryContainerLight = CyanPale
val onSecondaryContainerLight = CyanNight
val tertiaryLight = MagentaVivid
val onTertiaryLight = White
val tertiaryContainerLight = MagentaPale
val onTertiaryContainerLight = MagentaNight
val errorLight = RedError
val onErrorLight = White
val errorContainerLight = RedPale
val onErrorContainerLight = RedNight
val backgroundLight = LavenderWhite
val onBackgroundLight = InkIndigo
val surfaceLight = LavenderWhite
val onSurfaceLight = InkIndigo
val surfaceVariantLight = NeutralVariantLight
val onSurfaceVariantLight = NeutralVariant
val outlineLight = OutlineGreyLight
val outlineVariantLight = OutlineFaint
val scrimLight = Black
val inverseSurfaceLight = SlateIndigo
val inverseOnSurfaceLight = MistWhite
val inversePrimaryLight = IndigoLight
val surfaceDimLight = LavenderDim
val surfaceBrightLight = LavenderWhite
val surfaceContainerLowestLight = White
val surfaceContainerLowLight = LavenderMist
val surfaceContainerLight = LavenderHaze
val surfaceContainerHighLight = LavenderTint
val surfaceContainerHighestLight = LavenderDusk

// ---- Sombre ----
val primaryDark = IndigoLight
val onPrimaryDark = IndigoDeep
val primaryContainerDark = IndigoMid
val onPrimaryContainerDark = IndigoPale
val secondaryDark = CyanLight
val onSecondaryDark = CyanDark
val secondaryContainerDark = CyanMid
val onSecondaryContainerDark = CyanPale
val tertiaryDark = MagentaLight
val onTertiaryDark = MagentaDeep
val tertiaryContainerDark = MagentaMid
val onTertiaryContainerDark = MagentaPale
val errorDark = RedLight
val onErrorDark = RedDeep
val errorContainerDark = RedMid
val onErrorContainerDark = RedPale
val backgroundDark = IndigoBlack
val onBackgroundDark = MistLight
val surfaceDark = IndigoBlack
val onSurfaceDark = MistLight
val surfaceVariantDark = NeutralVariant
val onSurfaceVariantDark = OutlineFaint
val outlineDark = OutlineGreyDark
val outlineVariantDark = NeutralVariant
val scrimDark = Black
val inverseSurfaceDark = MistLight
val inverseOnSurfaceDark = SlateIndigo
val inversePrimaryDark = IndigoElectric
val surfaceDimDark = IndigoBlack
val surfaceBrightDark = IndigoBright
val surfaceContainerLowestDark = IndigoInk
val surfaceContainerLowDark = InkIndigo
val surfaceContainerDark = IndigoNightSurface
val surfaceContainerHighDark = IndigoSlate
val surfaceContainerHighestDark = IndigoSlateHigh
