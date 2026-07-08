package com.francotte.homecontroller.presentation.control

import com.francotte.homecontroller.domain.connection.EspConnectionState

/** État de l'écran de contrôle d'un appareil. */
data class ControlUiState(
    val connection: EspConnectionState = EspConnectionState.Connecting,
    val counter: Int? = null,          // dernière valeur reçue, null tant qu'aucune notif
    val ledOn: Boolean = false,        // état voulu de la LED (optimiste)
    val transientError: String? = null // message transitoire (ex. échec d'écriture LED)
)
