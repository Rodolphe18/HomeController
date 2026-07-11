package com.francotte.homecontroller.feature.devicedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LoadingState
import com.francotte.homecontroller.core.designsystem.component.StatusAction
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.model.EspConnectionState

@Composable
fun DeviceControlScreen(
    address: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceControlViewModel = hiltViewModel(
        creationCallback = { factory: DeviceControlViewModel.Factory -> factory.create(address) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = address, style = MaterialTheme.typography.titleMedium)

            when (val connection = uiState.connection) {
                EspConnectionState.Connecting ->
                    LoadingState(label = "Connexion en cours…")

                EspConnectionState.Connected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LED")
                        Switch(
                            checked = uiState.ledOn,
                            onCheckedChange = { viewModel.onLedToggle(it) }
                        )
                    }
                    Text("Compteur : ${uiState.counter ?: "—"}")
                    uiState.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                EspConnectionState.Disconnected -> StatusScreen(
                    icon = AppIcons.BluetoothDisabled,
                    title = "Déconnecté",
                    description = "La connexion avec l'appareil ESP32 est fermée.",
                    primaryAction = StatusAction("Reconnecter") { viewModel.onRetry() }
                )

                is EspConnectionState.Error -> StatusScreen(
                    icon = AppIcons.Warning,
                    title = "Erreur de connexion",
                    description = connection.message,
                    primaryAction = StatusAction("Réessayer") { viewModel.onRetry() }
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
