package com.francotte.homecontroller.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.francotte.homecontroller.data.scan.AndroidBleScanner
import com.francotte.homecontroller.domain.scan.BleScanner

/** Conteneur de dépendances applicatives, câblées à la main. */
class AppContainer(context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    val bleScanner: BleScanner = AndroidBleScanner(bluetoothManager?.adapter)
}
