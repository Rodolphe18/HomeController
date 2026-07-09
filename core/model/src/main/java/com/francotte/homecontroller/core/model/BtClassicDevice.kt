package com.francotte.homecontroller.core.model

/**
 * Un appareil Bluetooth Classic (BR/EDR) détecté lors d'une découverte.
 *
 * @property address MAC, identifiant unique
 * @property name    nom diffusé, ou null
 * @property rssi    force du signal si fournie (EXTRA_RSSI), sinon null
 * @property bonded  true si l'appareil est déjà appairé (BOND_BONDED)
 */
data class BtClassicDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val bonded: Boolean
)
