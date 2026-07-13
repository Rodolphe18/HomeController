package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LoadingState
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.ui.EntityCard

/**
 * Écran des entités : gère son propre [HomeAssistantEntitiesViewModel] (chargement, toggles,
 * temps réel). L'en-tête (titre + réglages) reste visible ; le bouton réglages remonte à
 * l'aiguilleur via [onEditConfig].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAssistantEntitiesContent(
    navigateToConfigScreen: () -> Unit,
    onEntityClick: (String) -> Unit,
    viewModel: HomeAssistantEntitiesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Home Assistant",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        // Pastille bleue (primaryContainer) autour de l'icône Settings.
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = navigateToConfigScreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(AppIcons.SettingsFilled),
                contentDescription = "Configuration",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    when (val s = state) {
        EntitiesUiState.Loading -> LoadingState(label = "Chargement…")

        is EntitiesUiState.Content -> {
            s.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            s.listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))

            PullToRefreshBox(
                isRefreshing = s.isRefreshing,
                onRefresh = viewModel::onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (s.items.isEmpty() && s.listError == null) {
                    StatusScreen(
                        icon = AppIcons.LightbulbEmpty,
                        title = "Aucun appareil",
                        description = "Aucune lumière ni prise commandable n'a été trouvée sur votre instance Home Assistant."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(s.items, key = { _, it -> it.entityId }) { index, entity ->
                            EntityCard(entity, index, viewModel::onToggle, onEntityClick)
                        }
                    }
                }
            }
        }
    }
}
