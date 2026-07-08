package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.model.HomeAssistantEntity

@Composable
fun HomeAssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                HomeAssistantUiState.Loading ->
                    CircularProgressIndicator()

                is HomeAssistantUiState.Unconfigured -> ConfigForm(
                    form = state.form,
                    onUrlChange = viewModel::onUrlChange,
                    onTokenChange = viewModel::onTokenChange,
                    onSubmit = viewModel::onTestAndSave
                )

                is HomeAssistantUiState.Entities -> EntitiesContent(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    onToggle = viewModel::onToggle,
                    onEditConfig = viewModel::onEditConfig
                )
            }
        }
    }
}

@Composable
private fun ConfigForm(
    form: ConfigFormState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connexion à Home Assistant", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = form.url,
            onValueChange = onUrlChange,
            label = { Text("URL (http://192.168.x.x:8123)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.token,
            onValueChange = onTokenChange,
            label = { Text("Jeton d'accès longue durée") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSubmit, enabled = !form.isTesting) {
            Text(if (form.isTesting) "Test en cours…" else "Tester & enregistrer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntitiesContent(
    state: HomeAssistantUiState.Entities,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEditConfig: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mes appareils", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onEditConfig) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuration")
        }
    }
    state.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.items.isEmpty() && state.listError == null) {
            Text("Aucune lumière ni prise trouvée.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.entityId }) { entity ->
                    EntityRow(entity, onToggle)
                }
            }
        }
    }
}

@Composable
private fun EntityRow(entity: HomeAssistantEntity, onToggle: (String, Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(entity.friendlyName, style = MaterialTheme.typography.titleSmall)
                Text(entity.entityId, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = entity.isOn, onCheckedChange = { onToggle(entity.entityId, it) })
        }
    }
}
