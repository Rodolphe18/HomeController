package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.model.BtClassicDevice

/** État de l'écran de scan Bluetooth Classic. */
sealed interface BtClassicUiState {
    /** Permissions non accordées. */
    data object PermissionRequired : BtClassicUiState

    /** Permissions OK mais Bluetooth désactivé. */
    data object BluetoothOff : BtClassicUiState

    /** Prêt à scanner, aucune passe en cours. */
    data object Idle : BtClassicUiState

    /** Découverte en cours ; [devices] triés. */
    data class Scanning(val devices: List<BtClassicDevice>) : BtClassicUiState

    /** Découverte terminée (une passe) ; [devices] triés ; relance manuelle. */
    data class Finished(val devices: List<BtClassicDevice>) : BtClassicUiState

    /** Échec de la découverte. */
    data class Error(val message: String) : BtClassicUiState
}
