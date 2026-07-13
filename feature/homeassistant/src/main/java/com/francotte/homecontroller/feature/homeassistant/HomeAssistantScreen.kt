package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.component.LoadingState

/**
 * Point d'entrée de l'onglet « Maison ». [viewModel] n'est qu'un **aiguilleur** : il choisit
 * l'écran (configuration / entités) ; chaque sous-écran obtient ensuite son propre ViewModel.
 */
@Composable
fun HomeAssistantScreen(
    onEntityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Transparent : le dégradé plein écran est peint par le shell (il passe derrière la status bar).
    // contentColor explicite car contentColorFor(Transparent) est indéfini → sinon le texte vire au noir.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { padding ->
        when (val state = uiState) {
            HomeAssistantUiState.Loading ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    LoadingState(label = stringResource(R.string.feature_homeassistant_loading))
                }

            // Le formulaire porte son propre Scaffold (top app bar) : on lui passe l'inset externe.
            is HomeAssistantUiState.Unconfigured -> HomeAssistantConfigurationScreen(
                canCancel = state.canCancel,
                outerPadding = padding,
                onCancel = viewModel::popBackConfigurationScreen,
                onConfigurationSaved = viewModel::onConfigurationSaved
            )

            HomeAssistantUiState.Entities ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                ) {
                    HomeAssistantEntitiesContent(
                        navigateToConfigScreen = viewModel::navigateToConfigurationScreen,
                        onEntityClick = onEntityClick
                    )
                }
        }
    }
}
