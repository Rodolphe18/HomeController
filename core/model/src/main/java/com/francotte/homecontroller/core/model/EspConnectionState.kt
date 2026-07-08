package com.francotte.homecontroller.core.model

/** État d'une session de connexion à un appareil ESP32. */
sealed interface EspConnectionState {
    data object Connecting : EspConnectionState
    data object Connected : EspConnectionState
    data object Disconnected : EspConnectionState
    data class Error(val message: String) : EspConnectionState
}
