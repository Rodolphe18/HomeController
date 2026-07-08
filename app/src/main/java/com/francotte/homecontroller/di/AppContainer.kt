package com.francotte.homecontroller.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.francotte.homecontroller.data.connection.AndroidEspDeviceClient
import com.francotte.homecontroller.data.scan.AndroidBleScanner
import com.francotte.homecontroller.domain.connection.EspDeviceClient
import com.francotte.homecontroller.domain.scan.BleScanner

/** Conteneur de dépendances applicatives, câblées à la main. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    val bleScanner: BleScanner = AndroidBleScanner(bluetoothManager?.adapter)

    /** Nouvelle session de connexion à chaque appel. */
    fun createEspDeviceClient(): EspDeviceClient = AndroidEspDeviceClient(appContext)
}
