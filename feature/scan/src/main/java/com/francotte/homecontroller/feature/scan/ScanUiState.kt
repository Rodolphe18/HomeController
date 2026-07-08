package com.francotte.homecontroller.feature.scan

import com.francotte.homecontroller.core.model.BleDevice

/** État de l'écran de scan BLE, source de vérité unique pour l'UI. */
sealed interface ScanUiState {
    /** Permissions Bluetooth non accordées. */
    data object PermissionRequired : ScanUiState

    /** Permissions OK mais Bluetooth désactivé. */
    data object BluetoothOff : ScanUiState

    /** Prêt à scanner, scan non démarré. */
    data object Idle : ScanUiState

    /** Scan en cours ; [devices] est la liste live triée par RSSI décroissant. */
    data class Scanning(val devices: List<BleDevice>) : ScanUiState

    /** Le scan a échoué. */
    data class Error(val message: String) : ScanUiState
}
