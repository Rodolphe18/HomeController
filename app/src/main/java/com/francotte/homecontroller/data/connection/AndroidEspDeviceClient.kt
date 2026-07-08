package com.francotte.homecontroller.data.connection

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.francotte.homecontroller.domain.connection.CounterCodec
import com.francotte.homecontroller.domain.connection.EspConnectionState
import com.francotte.homecontroller.domain.connection.EspDeviceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout

/** Implémentation Android de [EspDeviceClient] via BluetoothGatt. */
class AndroidEspDeviceClient(
    private val context: Context
) : EspDeviceClient {

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private val _state = MutableStateFlow<EspConnectionState>(EspConnectionState.Disconnected)
    override val state = _state

    private val _counter = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 16)
    override val counter = _counter

    private var gatt: BluetoothGatt? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingLedWrite: CompletableDeferred<Unit>? = null

    private val callback = object : android.bluetooth.BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = EspConnectionState.Error("Connexion échouée (code $status)")
                closeGatt()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = EspConnectionState.Disconnected
                    closeGatt()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(GattProfile.SERVICE_UUID)
            val led = service?.getCharacteristic(GattProfile.LED_UUID)
            val counterChar = service?.getCharacteristic(GattProfile.COUNTER_UUID)
            if (service == null || led == null || counterChar == null) {
                _state.value =
                    EspConnectionState.Error("Ce périphérique n'expose pas le profil HomeController")
                g.disconnect()
                return
            }
            ledCharacteristic = led
            enableCounterNotifications(g, counterChar)
            _state.value = EspConnectionState.Connected
        }

        // API < 33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == GattProfile.COUNTER_UUID) {
                _counter.tryEmit(CounterCodec.decode(ch.value))
            }
        }

        // API >= 33
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (ch.uuid == GattProfile.COUNTER_UUID) {
                _counter.tryEmit(CounterCodec.decode(value))
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (ch.uuid != GattProfile.LED_UUID) return
            val deferred = pendingLedWrite ?: return
            pendingLedWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(Unit)
            } else {
                deferred.completeExceptionally(IllegalStateException("Écriture LED refusée (code $status)"))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(address: String) {
        val device = adapter?.getRemoteDevice(address)
        if (device == null) {
            _state.value = EspConnectionState.Error("Adaptateur Bluetooth indisponible")
            return
        }
        closeGatt()
        _state.value = EspConnectionState.Connecting
        gatt = device.connectGatt(context, false, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect() {
        gatt?.disconnect()
        closeGatt()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun setLed(on: Boolean) {
        val g = gatt ?: throw IllegalStateException("Non connecté")
        val ch = ledCharacteristic ?: throw IllegalStateException("Caractéristique LED absente")
        val value = byteArrayOf(if (on) 0x01 else 0x00)
        val deferred = CompletableDeferred<Unit>()
        pendingLedWrite = deferred
        if (!writeLed(g, ch, value)) {
            pendingLedWrite = null
            throw IllegalStateException("Écriture LED refusée par la pile Bluetooth")
        }
        withTimeout(5_000) { deferred.await() }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableCounterNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(GattProfile.CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeLed(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                ch.value = value
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                g.writeCharacteristic(ch)
            }
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun closeGatt() {
        gatt?.close()
        gatt = null
        ledCharacteristic = null
    }
}
