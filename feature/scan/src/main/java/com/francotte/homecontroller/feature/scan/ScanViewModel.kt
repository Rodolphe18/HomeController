package com.francotte.homecontroller.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.BleScanException
import com.francotte.homecontroller.core.bluetooth.BleScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestre le scan BLE et expose l'état de l'écran.
 * Aucune dépendance Android : ne connaît que l'interface [BleScanner].
 *
 * L'UI est rafraîchie à [refreshIntervalMs] : le scanner émet à chaque paquet
 * BLE (bien trop souvent pour l'œil) ; on échantillonne à cet intervalle pour
 * une liste calme et lisible.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanner: BleScanner
) : ViewModel() {

    private val refreshIntervalMs: Long = 1_000L

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.PermissionRequired)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var hasPermissions = false
    private var isBluetoothEnabled = false
    private var scanJob: Job? = null

    /**
     * Informe le ViewModel des faits Android (fournis par l'écran).
     * Recalcule l'état de garde quand aucun scan n'est en cours ;
     * interrompt un scan actif si une condition tombe.
     */
    fun updateAvailability(hasPermissions: Boolean, isBluetoothEnabled: Boolean) {
        this.hasPermissions = hasPermissions
        this.isBluetoothEnabled = isBluetoothEnabled

        if (_uiState.value is ScanUiState.Scanning) {
            if (!hasPermissions || !isBluetoothEnabled) {
                stopScan()
            }
            return
        }

        _uiState.value = when {
            !hasPermissions -> ScanUiState.PermissionRequired
            !isBluetoothEnabled -> ScanUiState.BluetoothOff
            else -> ScanUiState.Idle
        }
    }

    fun startScan() {
        if (!hasPermissions || !isBluetoothEnabled) return
        if (scanJob != null) return

        _uiState.value = ScanUiState.Scanning(emptyList())
        val stabilizer = RssiStabilizer()
        scanJob = viewModelScope.launch {
            scanner.scan()
                .sample(refreshIntervalMs)
                .onEach { snapshot ->
                    _uiState.value = ScanUiState.Scanning(stabilizer.update(snapshot))
                }
                .catch { throwable ->
                    val message = when (throwable) {
                        is BleScanException -> "Échec du scan (code ${throwable.errorCode})"
                        else -> throwable.message ?: "Échec du scan"
                    }
                    _uiState.value = ScanUiState.Error(message)
                }
                .collect()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.value = ScanUiState.Idle
    }
}
