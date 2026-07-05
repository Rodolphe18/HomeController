package com.francotte.homecontroller.data.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import com.francotte.homecontroller.domain.model.BleDevice
import com.francotte.homecontroller.domain.scan.BleScanException
import com.francotte.homecontroller.domain.scan.BleScanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Implémentation Android de [BleScanner] via BluetoothLeScanner.
 * Le scan est actif tant que le Flow est collecté ; il s'arrête à l'annulation.
 */
class AndroidBleScanner(
    private val bluetoothAdapter: BluetoothAdapter?
) : BleScanner {

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            close(BleScanException(ERROR_ADAPTER_UNAVAILABLE))
            return@callbackFlow
        }

        // Déduplication par adresse MAC : un appareil vu N fois = 1 entrée, RSSI actualisé.
        val found = LinkedHashMap<String, BleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                found[device.address] = BleDevice(
                    address = device.address,
                    name = device.name, // null si non diffusé ; nécessite BLUETOOTH_CONNECT
                    rssi = result.rssi
                )
                trySend(found.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner.startScan(null, settings, callback)

        awaitClose { leScanner.stopScan(callback) }
    }

    private companion object {
        const val ERROR_ADAPTER_UNAVAILABLE = -1
    }
}
