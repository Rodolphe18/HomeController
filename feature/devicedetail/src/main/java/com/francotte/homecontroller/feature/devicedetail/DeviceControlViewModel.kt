package com.francotte.homecontroller.feature.devicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.EspDeviceClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pilote une session de connexion à un ESP32 : expose l'état, le compteur,
 * et commande la LED (avec retour optimiste). Sans dépendance Android.
 */
@HiltViewModel(assistedFactory = DeviceControlViewModel.Factory::class)
class DeviceControlViewModel @AssistedInject constructor(
    @Assisted private val address: String,
    private val client: EspDeviceClient
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(address: String): DeviceControlViewModel
    }

    private val ledOn = MutableStateFlow(false)
    private val transientError = MutableStateFlow<Int?>(null)

    val uiState = combine(
        client.state,
        client.counter.map<Int, Int?> { it }.onStart { emit(null) },
        ledOn,
        transientError
    ) { connection, counter, led, error ->
        ControlUiState(connection = connection, counter = counter, ledOn = led, transientError = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ControlUiState()
    )

    init {
        client.connect(address)
    }

    fun onLedToggle(on: Boolean) {
        ledOn.value = on            // optimiste : retour visuel immédiat
        transientError.value = null
        viewModelScope.launch {
            try {
                client.setLed(on)
            } catch (t: Throwable) {
                ledOn.value = !on   // échec → on revient en arrière
                transientError.value = R.string.feature_devicedetail_led_write_failed
            }
        }
    }

    /** Relance la connexion (bouton Réessayer / Reconnecter). */
    fun onRetry() {
        client.connect(address)
    }

    override fun onCleared() {
        client.disconnect()
    }
}
