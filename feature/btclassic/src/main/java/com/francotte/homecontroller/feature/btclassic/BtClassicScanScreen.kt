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
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.StatusAction
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.model.BtClassicDevice
import com.francotte.homecontroller.core.ui.BtClassicDeviceCard

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
        when (val state = uiState) {
            BtClassicUiState.PermissionRequired -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.Lock,
                title = "Permission requise",
                description = "HomeController a besoin de la permission Bluetooth pour scanner les appareils.",
                primaryAction = StatusAction("Accorder la permission") {
                    permissionLauncher.launch(requiredScanPermissions())
                }
            )

            BtClassicUiState.BluetoothOff -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.BluetoothDisabled,
                title = "Bluetooth désactivé",
                description = "Activez le Bluetooth pour scanner les appareils Bluetooth Classic.",
                primaryAction = StatusAction("Activer le Bluetooth") {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            )

            BtClassicUiState.Idle -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.Bluetooth,
                title = "Prêt à scanner",
                description = "Lancez une recherche pour découvrir les appareils Bluetooth Classic appairés et à proximité.",
                primaryAction = StatusAction("Scanner") { viewModel.startScan() }
            )

            is BtClassicUiState.Scanning -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
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

            is BtClassicUiState.Finished ->
                if (state.devices.isEmpty()) {
                    StatusScreen(
                        modifier = Modifier.padding(innerPadding),
                        icon = AppIcons.Bluetooth,
                        title = "Aucun appareil trouvé",
                        description = "Aucun appareil Bluetooth Classic n'a été détecté. Vérifiez qu'ils sont allumés et visibles.",
                        primaryAction = StatusAction("Scanner à nouveau") { viewModel.startScan() }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Button(onClick = { viewModel.startScan() }) { Text("Scanner à nouveau") }
                        Spacer(Modifier.height(16.dp))
                        DeviceList(state.devices)
                    }
                }

            is BtClassicUiState.Error -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.Warning,
                title = "Une erreur est survenue",
                description = state.message,
                primaryAction = StatusAction("Réessayer") {
                    refreshAvailability()
                    viewModel.startScan()
                }
            )
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
            items(paired, key = { it.address }) { device -> BtClassicDeviceCard(device) }
        }
        if (discovered.isNotEmpty()) {
            item(key = "header_discovered") { SectionHeader("Découverts") }
            items(discovered, key = { it.address }) { device -> BtClassicDeviceCard(device) }
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
