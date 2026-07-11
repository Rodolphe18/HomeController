package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

@Composable
fun EntityDetailScreen(
    entityId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntityDetailViewModel = hiltViewModel(
        creationCallback = { factory: EntityDetailViewModel.Factory -> factory.create(entityId) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = uiState) {
                EntityDetailUiState.Loading ->
                    LoadingState(label = "Chargement…")

                is EntityDetailUiState.Error -> StatusScreen(
                    icon = AppIcons.Warning,
                    title = "Impossible de charger l'appareil",
                    description = state.message,
                    primaryAction = StatusAction("Retour", onBack)
                )

                is EntityDetailUiState.Content -> {
                    Text(state.friendlyName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.isOn) "Allumée" else "Éteinte",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (state.supportsBrightness) {
                        if (state.isOn) {
                            Text("${state.brightnessPercent} %", style = MaterialTheme.typography.headlineSmall)
                        }
                        BrightnessGauge(
                            percent = state.brightnessPercent,
                            onValueChange = viewModel::onBrightnessDrag,
                            onValueChangeFinished = viewModel::onBrightnessCommit,
                            modifier = Modifier
                                .fillMaxHeight(0.6f)
                                .width(130.dp)
                        )
                    } else {
                        Switch(checked = state.isOn, onCheckedChange = viewModel::onToggle)
                    }
                    state.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onBack) { Text("Retour") }
                }
            }
        }
    }
}
