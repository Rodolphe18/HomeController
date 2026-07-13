package com.francotte.homecontroller.feature.btlowenergy

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
class BleScanViewModel @Inject constructor(
    private val scanner: BleScanner
) : ViewModel() {

    private val refreshIntervalMs: Long = 1_000L

    private val _uiState = MutableStateFlow<BleScanUiState>(BleScanUiState.PermissionRequired)
    val uiState: StateFlow<BleScanUiState> = _uiState.asStateFlow()

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

        if (_uiState.value is BleScanUiState.Scanning) {
            if (!hasPermissions || !isBluetoothEnabled) {
                stopScan()
            }
            return
        }

        _uiState.value = when {
            !hasPermissions -> BleScanUiState.PermissionRequired
            !isBluetoothEnabled -> BleScanUiState.BluetoothOff
            else -> BleScanUiState.Idle
        }
    }

    fun startScan() {
        if (!hasPermissions || !isBluetoothEnabled) return
        if (scanJob != null) return

        _uiState.value = BleScanUiState.Scanning(emptyList())
        val stabilizer = BleRssiStabilizer()
        scanJob = viewModelScope.launch {
            scanner.scan()
                .sample(refreshIntervalMs)
                .onEach { snapshot ->
                    _uiState.value = BleScanUiState.Scanning(stabilizer.update(snapshot))
                }
                .catch { throwable ->
                    // Le texte affiché est décidé ici (ressource + arg éventuel), pas hérité du message brut.
                    _uiState.value = when (throwable) {
                        is BleScanException -> BleScanUiState.Error(
                            R.string.feature_btlowenergy_scan_failed_code,
                            listOf(throwable.errorCode)
                        )
                        else -> BleScanUiState.Error(R.string.feature_btlowenergy_scan_failed)
                    }
                }
                .collect()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.value = BleScanUiState.Idle
    }
}
