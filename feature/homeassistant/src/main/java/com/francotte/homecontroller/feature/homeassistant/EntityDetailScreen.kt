package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.francotte.homecontroller.core.designsystem.component.BrightnessGauge
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
                    LoadingState(label = stringResource(R.string.feature_homeassistant_loading))

                is EntityDetailUiState.Error -> StatusScreen(
                    icon = AppIcons.Warning,
                    title = stringResource(R.string.feature_homeassistant_detail_load_error_title),
                    description = stringResource(state.messageRes),
                    primaryAction = StatusAction(stringResource(R.string.feature_homeassistant_back), onBack)
                )

                is EntityDetailUiState.Content -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isOn) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(state.friendlyName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                stringResource(
                                    if (state.isOn) R.string.feature_homeassistant_state_on
                                    else R.string.feature_homeassistant_state_off
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (state.supportsBrightness && state.isOn) {
                                Text(
                                    stringResource(
                                        R.string.feature_homeassistant_brightness_percent,
                                        state.brightnessPercent
                                    ),
                                    style = MaterialTheme.typography.displaySmall
                                )
                            }
                        }
                    }
                    if (state.supportsBrightness) {
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
                    state.transientError?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onBack) { Text(stringResource(R.string.feature_homeassistant_back)) }
                }
            }
        }
    }
}
