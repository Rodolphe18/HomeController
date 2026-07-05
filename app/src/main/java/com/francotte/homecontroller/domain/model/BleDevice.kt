package com.francotte.homecontroller.domain.model

/**
 * Un périphérique BLE détecté lors d'un scan.
 *
 * @property address adresse MAC, identifiant unique du périphérique
 * @property name    nom diffusé par le périphérique, ou null si non diffusé
 * @property rssi    force du signal reçu, en dBm (plus proche de 0 = plus fort)
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int
)
