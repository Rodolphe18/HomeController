package com.francotte.homecontroller.feature.devicedetail

import com.francotte.homecontroller.core.bluetooth.EspDeviceClient
import com.francotte.homecontroller.core.model.EspConnectionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEspDeviceClient : EspDeviceClient {
    val stateFlow = MutableStateFlow<EspConnectionState>(EspConnectionState.Connecting)
    val counterFlow = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 8)

    override val state = stateFlow
    override val counter = counterFlow

    var connectedAddress: String? = null
    var disconnectCalled = false
    val ledWrites = mutableListOf<Boolean>()
    var failLed = false

    override fun connect(address: String) { connectedAddress = address }
    override fun disconnect() { disconnectCalled = true }
    override suspend fun setLed(on: Boolean) {
        if (failLed) throw RuntimeException("échec simulé")
        ledWrites.add(on)
    }
}
