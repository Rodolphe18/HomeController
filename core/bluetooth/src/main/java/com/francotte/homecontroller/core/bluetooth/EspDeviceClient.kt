package com.francotte.homecontroller.core.bluetooth

import com.francotte.homecontroller.core.model.EspConnectionState
import kotlinx.coroutines.flow.Flow

/**
 * Client de connexion à un ESP32 exposant le profil HomeController.
 * Une instance = une session. L'implémentation vit dans la couche data.
 */
interface EspDeviceClient {
    val state: Flow<EspConnectionState>
    val counter: Flow<Int>
    fun connect(address: String)
    fun disconnect()
    suspend fun setLed(on: Boolean)
}
