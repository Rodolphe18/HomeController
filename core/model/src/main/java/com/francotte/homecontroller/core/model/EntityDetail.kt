package com.francotte.homecontroller.core.model

/**
 * Vue détaillée d'une entité pour l'écran de détail.
 *
 * @property supportsBrightness true si l'entité a un variateur (simplification : domain == "light").
 * @property brightnessPercent  0..100 ; 0 quand éteint.
 */
data class EntityDetail(
    val entityId: String,
    val friendlyName: String,
    val domain: String,
    val isOn: Boolean,
    val supportsBrightness: Boolean,
    val brightnessPercent: Int
)
