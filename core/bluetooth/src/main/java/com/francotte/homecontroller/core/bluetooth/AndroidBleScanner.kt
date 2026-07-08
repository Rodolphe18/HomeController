package com.francotte.homecontroller.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import com.francotte.homecontroller.core.model.BleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class AndroidBleScanner @Inject constructor(
    @ApplicationContext context: Context
) : BleScanner {

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            close(BleScanException(ERROR_ADAPTER_UNAVAILABLE))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, BleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                found[device.address] = BleDevice(
                    address = device.address,
                    name = device.name,
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
