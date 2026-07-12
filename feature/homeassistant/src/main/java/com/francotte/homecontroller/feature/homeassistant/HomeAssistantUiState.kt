package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.runtime.Immutable
import com.francotte.homecontroller.core.model.HomeAssistantEntity

/** État de formulaire de configuration. */
@Immutable
data class ConfigFormState(
    val url: String = "",
    val token: String = "",
    val isTesting: Boolean = false,
    val error: String? = null
)

/** État de l'écran Home Assistant. */
sealed interface HomeAssistantUiState {
    /** Lecture initiale de la configuration. */
    data object Loading : HomeAssistantUiState

    /**
     * Pas (ou plus) de configuration : on montre le formulaire.
     * [canCancel] vaut true quand une config existe déjà (édition) : on peut alors annuler
     * et revenir à la liste ; false lors de la configuration initiale (rien derrière).
     */
    @Immutable
    data class Unconfigured(
        val form: ConfigFormState,
        val canCancel: Boolean = false
    ) : HomeAssistantUiState

    /** Configuré : liste des entités commandables. */
    @Immutable
    data class Entities(
        val items: List<HomeAssistantEntity>,
        val isRefreshing: Boolean = false,
        val listError: String? = null,
        val transientError: String? = null
    ) : HomeAssistantUiState
}
