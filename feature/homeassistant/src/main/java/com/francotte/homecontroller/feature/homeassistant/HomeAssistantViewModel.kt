package com.francotte.homecontroller.feature.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.domain.ObserveConfigUseCase
import com.francotte.homecontroller.core.model.HomeAssistantException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel « aiguilleur » du parcours Home Assistant : il ne fait QUE choisir l'écran à afficher
 * — configuration ([HomeAssistantUiState.Unconfigured]) ou liste d'entités
 * ([HomeAssistantUiState.Entities]) — à partir de la présence d'une config et d'un drapeau
 * d'édition. Le contenu de chaque écran est géré par son propre ViewModel
 * ([HomeAssistantConfigurationViewModel], [HomeAssistantEntitiesViewModel]).
 */
@HiltViewModel
class HomeAssistantViewModel @Inject constructor(
    observeConfig: ObserveConfigUseCase
) : ViewModel() {

    /** Édition d'une config existante, demandée depuis l'écran des entités (bouton réglages). */
    private val editing = MutableStateFlow(false)

    val uiState: StateFlow<HomeAssistantUiState> = combine(
        observeConfig(),
        editing
    ) { config, editing ->
        when {
            // Pas de config → configuration initiale (rien derrière, donc non annulable).
            config == null -> HomeAssistantUiState.Unconfigured(canCancel = false)
            // Config présente + édition demandée → formulaire annulable.
            editing -> HomeAssistantUiState.Unconfigured(canCancel = true)
            // Config présente sans édition → liste des entités.
            else -> HomeAssistantUiState.Entities
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeAssistantUiState.Loading)

    /** Ouvre le formulaire pour modifier une config existante. */
    fun navigateToConfigurationScreen() {
        editing.value = true
    }

    /** Annule l'édition et revient à la liste (sans effet si aucune config n'existe encore). */
    fun popBackConfigurationScreen() {
        editing.value = false
    }

    /** Après une sauvegarde réussie : on quitte le formulaire pour la liste. */
    fun onConfigurationSaved() {
        editing.value = false
    }
}

/** Message utilisateur pour une erreur (typée ou générique). Partagé par les VMs du feature. */
internal fun Throwable.toMessage(): String = when (this) {
    is HomeAssistantException.Unauthorized -> "Token rejected. Check your access token."
    is HomeAssistantException.Unreachable -> "Home Assistant unreachable. Make sure your device's Wi-Fi is on."
    is HomeAssistantException.NotConfigured -> "Home Assistant doesn't seem to be configured."
    else -> message ?: "Unknown error."
}
