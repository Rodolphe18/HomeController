package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LoadingState
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.model.HomeAssistantEntity

@Composable
fun HomeAssistantScreen(
    onEntityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeAssistantViewModel = hiltViewModel()
) {
    // Collecte lifecycle-aware : STARTED (écran visible) + 5 s de grâce côté ViewModel
    // (WhileSubscribed) pilotent à la fois l'affichage et la vie de la connexion WebSocket.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Transparent : le dégradé plein écran est peint par le shell (il passe derrière la status bar).
    // contentColor explicite car contentColorFor(Transparent) est indéfini → sinon le texte vire au noir.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                HomeAssistantUiState.Loading ->
                    LoadingState(label = "Chargement…")

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
                    onEditConfig = viewModel::onEditConfig,
                    onEntityClick = onEntityClick
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
    onEditConfig: () -> Unit,
    onEntityClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Mes appareils",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onEditConfig),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(AppIcons.SettingsFilled),
                contentDescription = "Configuration",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    state.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.items.isEmpty() && state.listError == null) {
            StatusScreen(
                icon = AppIcons.LightbulbEmpty,
                title = "Aucun appareil",
                description = "Aucune lumière ni prise commandable n'a été trouvée sur votre instance Home Assistant."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.items, key = { _, it -> it.entityId }) { index, entity ->
                    EntityRow(entity, index, onToggle, onEntityClick)
                }
            }
        }
    }
}

@Composable
private fun EntityRow(
    entity: HomeAssistantEntity,
    accentIndex: Int,
    onToggle: (String, Boolean) -> Unit,
    onClick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // Chaque appareil reçoit un accent distinct, cyclé par sa position dans la liste :
    // indigo (primaire) → magenta (tertiaire) → cyan (secondaire). Ainsi l'avatar est
    // toujours coloré, et allumer un 2e appareil donne une couleur de carte différente.
    val accent = when (accentIndex % 3) {
        0 -> RowAccent(cs.primary, cs.onPrimary, cs.primaryContainer, cs.onPrimaryContainer)
        1 -> RowAccent(cs.tertiary, cs.onTertiary, cs.tertiaryContainer, cs.onTertiaryContainer)
        else -> RowAccent(
            cs.secondary,
            cs.onSecondary,
            cs.secondaryContainer,
            cs.onSecondaryContainer
        )
    }
    // Icône selon le type : puce pour l'ESP32, ampoule pour les lumières, prise sinon.
    val icon = when {
        entity.entityId.contains("esp32", ignoreCase = true) -> AppIcons.Chip
        entity.domain == "light" -> AppIcons.LightbulbEmpty
        else -> AppIcons.Plug
    }
    val containerColor = if (entity.isOn) accent.container else cs.surfaceContainerHigh
    val contentColor = if (entity.isOn) accent.onContainer else cs.onSurface
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(entity.entityId) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.avatar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = accent.onAvatar,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entity.friendlyName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    text = if (entity.isOn) "Allumée" else "Éteinte",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Switch(
                checked = entity.isOn,
                onCheckedChange = { onToggle(entity.entityId, it) },
                modifier = Modifier.scale(1.2f),
                // Piste pleine sans contour extérieur, comme la maquette.
                colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent)
            )
        }
    }
}

/** Regroupe les 4 teintes d'un accent de ligne (avatar + carte allumée). */
private data class RowAccent(
    val avatar: Color,
    val onAvatar: Color,
    val container: Color,
    val onContainer: Color
)
