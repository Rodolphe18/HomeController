package com.francotte.homecontroller.feature.btclassic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.BtClassicScanner
import com.francotte.homecontroller.core.model.BtClassicDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BtClassicScanViewModel @Inject constructor(
    private val scanner: BtClassicScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow<BtClassicUiState>(BtClassicUiState.PermissionRequired)
    val uiState: StateFlow<BtClassicUiState> = _uiState.asStateFlow()

    private var hasPermissions = false
    private var isBluetoothEnabled = false
    private var scanJob: Job? = null

    /** Faits Android fournis par l'écran (permissions, Bluetooth activé). */
    fun updateAvailability(hasPermissions: Boolean, isBluetoothEnabled: Boolean) {
        this.hasPermissions = hasPermissions
        this.isBluetoothEnabled = isBluetoothEnabled

        if (_uiState.value is BtClassicUiState.Scanning) {
            if (!hasPermissions || !isBluetoothEnabled) stopScan()
            return
        }

        _uiState.value = when {
            !hasPermissions -> BtClassicUiState.PermissionRequired
            !isBluetoothEnabled -> BtClassicUiState.BluetoothOff
            _uiState.value is BtClassicUiState.Finished -> _uiState.value  // conserve les résultats
            else -> BtClassicUiState.Idle
        }
    }

    fun startScan() {
        if (!hasPermissions || !isBluetoothEnabled) return
        if (scanJob != null) return

        _uiState.value = BtClassicUiState.Scanning(emptyList())
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { devices ->
                    _uiState.value = BtClassicUiState.Scanning(sortDevices(devices))
                }
                // Flow terminé (découverte finie) → Finished avec la dernière liste.
                val last = (_uiState.value as? BtClassicUiState.Scanning)?.devices ?: emptyList()
                _uiState.value = BtClassicUiState.Finished(last)
            } catch (c: CancellationException) {
                throw c   // annulation (stopScan / VM détruit) : pas une erreur
            } catch (t: Throwable) {
                // Le texte affiché est décidé ici (ressource), pas hérité du message d'exception.
                _uiState.value = BtClassicUiState.Error(R.string.feature_btclassic_scan_failed)
            } finally {
                scanJob = null
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        val last = (_uiState.value as? BtClassicUiState.Scanning)?.devices ?: emptyList()
        _uiState.value = BtClassicUiState.Finished(last)
    }

    private fun sortDevices(devices: List<BtClassicDevice>): List<BtClassicDevice> =
        devices.sortedWith(
            compareByDescending<BtClassicDevice> { it.bonded }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.address }
        )
}
