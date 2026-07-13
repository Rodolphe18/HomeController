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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                    LoadingState(label = stringResource(R.string.feature_devicedetail_connecting))

                EspConnectionState.Connected -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.ledOn) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.feature_devicedetail_led),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Switch(
                                    checked = uiState.ledOn,
                                    onCheckedChange = { viewModel.onLedToggle(it) }
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.feature_devicedetail_counter,
                                    uiState.counter?.toString()
                                        ?: stringResource(R.string.feature_devicedetail_counter_none)
                                )
                            )
                        }
                    }
                    uiState.transientError?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                }

                EspConnectionState.Disconnected -> StatusScreen(
                    icon = AppIcons.BluetoothDisabled,
                    title = stringResource(R.string.feature_devicedetail_disconnected_title),
                    description = stringResource(R.string.feature_devicedetail_disconnected_description),
                    primaryAction = StatusAction(stringResource(R.string.feature_devicedetail_reconnect)) { viewModel.onRetry() }
                )

                is EspConnectionState.Error -> StatusScreen(
                    icon = AppIcons.Warning,
                    title = stringResource(R.string.feature_devicedetail_error_title),
                    description = connection.message,
                    primaryAction = StatusAction(stringResource(R.string.feature_devicedetail_retry)) { viewModel.onRetry() }
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.feature_devicedetail_back)) }
        }
    }
}
