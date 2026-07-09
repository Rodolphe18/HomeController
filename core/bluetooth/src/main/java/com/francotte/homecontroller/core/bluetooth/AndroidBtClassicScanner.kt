package com.francotte.homecontroller.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.francotte.homecontroller.core.model.BtClassicDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class AndroidBtClassicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : BtClassicScanner {

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun scan(): Flow<List<BtClassicDevice>> = callbackFlow {
        val bt = adapter
        if (bt == null) {
            close(BtClassicScanException("Adaptateur Bluetooth indisponible"))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, BtClassicDevice>()

        // Les appareils déjà appairés ne répondent pas à l'inquiry (non découvrables) : on les
        // ajoute directement depuis bondedDevices, avec leur nom et le badge « Appairé ».
        bt.bondedDevices?.forEach { device ->
            found[device.address] = BtClassicDevice(
                address = device.address,
                name = device.name,
                rssi = null,
                bonded = true
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        ) ?: return
                        // Filtrer le bruit BLE anonyme : on ne garde que le Bluetooth Classic
                        // (Classic/Dual, ou type encore non résolu), pas les appareils LE purs.
                        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) return
                        val rssi = if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) {
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        } else {
                            null
                        }
                        // Le nom peut arriver dans l'extra de l'inquiry avant d'être mis en cache
                        // sur le device, voire dans un broadcast ultérieur pour la même adresse.
                        val name = device.name ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        val existing = found[device.address]
                        found[device.address] = BtClassicDevice(
                            address = device.address,
                            // Ne jamais écraser une info déjà connue par une valeur nulle plus tardive.
                            name = name ?: existing?.name,
                            rssi = rssi ?: existing?.rssi,
                            bonded = device.bondState == BluetoothDevice.BOND_BONDED
                        )
                        trySend(found.values.toList())
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        close()   // une seule passe : le Flow se termine (pas de relance auto)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // RECEIVER_EXPORTED est requis : ACTION_FOUND / ACTION_DISCOVERY_FINISHED sont
        // diffusés par le processus système Bluetooth (com.android.bluetooth), pas par notre
        // app. Avec RECEIVER_NOT_EXPORTED, ContextCompat protège le receiver par une permission
        // signature que le système ne détient pas, et ses broadcasts sont refusés (Permission
        // Denial) : aucun appareil trouvé et la découverte ne se termine jamais.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Afficher les appareils appairés tout de suite, sans attendre la découverte.
        trySend(found.values.toList())

        if (!bt.startDiscovery()) {
            close(BtClassicScanException("Impossible de démarrer la découverte"))
            return@callbackFlow
        }

        awaitClose {
            bt.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }
}
