package com.francotte.homecontroller.feature.btclassic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.francotte.homecontroller.core.model.BtClassicDevice

/** Permissions runtime selon la version Android (mêmes que le scan BLE). */
private fun requiredScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Sans neverForLocation, la découverte Classic (startDiscovery) n'aboutit que si la
        // localisation est accordée. Sur Android 12+, FINE doit être demandée avec COARSE.
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
fun BtClassicScanScreen(
    modifier: Modifier = Modifier,
    viewModel: BtClassicScanViewModel = hiltViewModel()
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
                BtClassicUiState.PermissionRequired -> GateMessage(
                    message = "L'application a besoin de la permission Bluetooth pour scanner les appareils.",
                    actionLabel = "Accorder la permission",
                    onAction = { permissionLauncher.launch(requiredScanPermissions()) }
                )

                BtClassicUiState.BluetoothOff -> GateMessage(
                    message = "Le Bluetooth est désactivé.",
                    actionLabel = "Activer le Bluetooth",
                    onAction = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                )

                BtClassicUiState.Idle -> {
                    Button(onClick = { viewModel.startScan() }) { Text("Scanner") }
                    Spacer(Modifier.height(16.dp))
                    Text("Prêt à scanner les appareils Bluetooth Classic.")
                }

                is BtClassicUiState.Scanning -> {
                    Button(onClick = { viewModel.stopScan() }) { Text("Arrêter") }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recherche d'appareils…")
                    }
                    Spacer(Modifier.height(12.dp))
                    DeviceList(state.devices)
                }

                is BtClassicUiState.Finished -> {
                    Button(onClick = { viewModel.startScan() }) { Text("Scanner à nouveau") }
                    Spacer(Modifier.height(16.dp))
                    if (state.devices.isEmpty()) {
                        Text("Aucun appareil trouvé.")
                    } else {
                        DeviceList(state.devices)
                    }
                }

                is BtClassicUiState.Error -> GateMessage(
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
private fun DeviceList(devices: List<BtClassicDevice>) {
    val paired = devices.filter { it.bonded }
    val discovered = devices.filter { !it.bonded }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paired.isNotEmpty()) {
            item(key = "header_paired") { SectionHeader("Appairés") }
            items(paired, key = { it.address }) { device -> DeviceRow(device) }
        }
        if (discovered.isNotEmpty()) {
            item(key = "header_discovered") { SectionHeader("Découverts") }
            items(discovered, key = { it.address }) { device -> DeviceRow(device) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DeviceRow(device: BtClassicDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Inconnu", style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = device.rssi?.let { "$it dBm" } ?: "— dBm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (device.bonded) {
                Text(
                    text = "Appairé",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
