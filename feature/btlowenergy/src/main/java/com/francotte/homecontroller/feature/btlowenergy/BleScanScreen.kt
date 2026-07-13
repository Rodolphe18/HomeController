package com.francotte.homecontroller.feature.btlowenergy

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LoadingState
import com.francotte.homecontroller.core.designsystem.component.StatusAction
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.model.BleDevice
import com.francotte.homecontroller.core.ui.BleDeviceCard

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
fun BleScanScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BleScanViewModel = hiltViewModel()
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
            BleScanUiState.PermissionRequired -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.Lock,
                title = "Permission requise",
                description = "HomeController a besoin de la permission Bluetooth pour trouver vos appareils à proximité.",
                primaryAction = StatusAction("Accorder la permission") {
                    permissionLauncher.launch(requiredScanPermissions())
                }
            )

            BleScanUiState.BluetoothOff -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.BluetoothDisabled,
                title = "Bluetooth désactivé",
                description = "Activez le Bluetooth pour scanner les appareils autour de vous.",
                primaryAction = StatusAction("Activer le Bluetooth") {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            )

            BleScanUiState.Idle -> StatusScreen(
                modifier = Modifier.padding(innerPadding),
                icon = AppIcons.Bluetooth,
                title = "Prêt à scanner",
                description = "Lancez une recherche pour découvrir les appareils Bluetooth Low Energy autour de vous.",
                primaryAction = StatusAction("Démarrer le scan") { viewModel.startScan() }
            )

            is BleScanUiState.Scanning ->
                if (state.devices.isEmpty()) {
                    LoadingState(
                        modifier = Modifier.padding(innerPadding),
                        label = "Recherche d'appareils…"
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Button(onClick = { viewModel.stopScan() }) { Text("Arrêter le scan") }
                        Spacer(Modifier.height(16.dp))
                        DeviceList(state.devices, onDeviceClick)
                    }
                }

            is BleScanUiState.Error -> StatusScreen(
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
private fun DeviceList(devices: List<BleDevice>, onDeviceClick: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            BleDeviceCard(device, onClick = { onDeviceClick(device.address) })
        }
    }
}
