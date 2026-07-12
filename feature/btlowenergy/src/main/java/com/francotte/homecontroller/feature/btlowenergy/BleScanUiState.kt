package com.francotte.homecontroller.feature.btlowenergy

import androidx.compose.runtime.Immutable
import com.francotte.homecontroller.core.model.BleDevice

/** État de l'écran de scan BLE, source de vérité unique pour l'UI. */
sealed interface BleScanUiState {
    /** Permissions Bluetooth non accordées. */
    data object PermissionRequired : BleScanUiState

    /** Permissions OK mais Bluetooth désactivé. */
    data object BluetoothOff : BleScanUiState

    /** Prêt à scanner, scan non démarré. */
    data object Idle : BleScanUiState

    /** Scan en cours ; [devices] est la liste live triée par RSSI décroissant. */
    @Immutable
    data class Scanning(val devices: List<BleDevice>) : BleScanUiState

    /** Le scan a échoué. */
    @Immutable
    data class Error(val message: String) : BleScanUiState
}
