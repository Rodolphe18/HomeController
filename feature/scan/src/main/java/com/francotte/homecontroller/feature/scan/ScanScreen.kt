package com.francotte.homecontroller.feature.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.model.BleDevice

/** Permissions runtime à demander, selon la version Android. */
private fun requiredScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Sans neverForLocation, la découverte n'aboutit que si la localisation est accordée.
        // Sur Android 12+, FINE doit être demandée avec COARSE, sinon le système l'ignore.
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasScanPermissions(): Boolean =
    requiredScanPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

private fun Context.isBluetoothEnabled(): Boolean {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    return manager?.adapter?.isEnabled == true
}

@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun refreshAvailability() {
        viewModel.updateAvailability(
            hasPermissions = context.hasScanPermissions(),
            isBluetoothEnabled = context.isBluetoothEnabled()
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAvailability() }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAvailability() }

    LaunchedEffect(Unit) { refreshAvailability() }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                ScanUiState.PermissionRequired -> GateMessage(
                    message = "L'application a besoin de la permission Bluetooth pour scanner les appareils.",
                    actionLabel = "Accorder la permission",
                    onAction = { permissionLauncher.launch(requiredScanPermissions()) }
                )

                ScanUiState.BluetoothOff -> GateMessage(
                    message = "Le Bluetooth est désactivé.",
                    actionLabel = "Activer le Bluetooth",
                    onAction = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                )

                ScanUiState.Idle -> {
                    ScanButton(isScanning = false, onClick = { viewModel.startScan() })
                    Spacer(Modifier.height(16.dp))
                    Text("Prêt à scanner.")
                }

                is ScanUiState.Scanning -> {
                    ScanButton(isScanning = true, onClick = { viewModel.stopScan() })
                    Spacer(Modifier.height(16.dp))
                    if (state.devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recherche d'appareils…")
                        }
                    } else {
                        DeviceList(state.devices, onDeviceClick)
                    }
                }

                is ScanUiState.Error -> GateMessage(
                    message = state.message,
                    actionLabel = "Réessayer",
                    onAction = {
                        refreshAvailability()
                        viewModel.startScan()
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanButton(isScanning: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(if (isScanning) "Arrêter le scan" else "Démarrer le scan")
    }
}

@Composable
private fun DeviceList(devices: List<BleDevice>, onDeviceClick: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            DeviceRow(device, onClick = { onDeviceClick(device.address) })
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.name ?: "Inconnu",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GateMessage(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
