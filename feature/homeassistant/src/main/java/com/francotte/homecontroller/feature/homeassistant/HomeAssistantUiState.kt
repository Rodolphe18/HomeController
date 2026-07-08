package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.HomeAssistantEntity

/** État de formulaire de configuration. */
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

    /** Pas (ou plus) de configuration : on montre le formulaire. */
    data class Unconfigured(val form: ConfigFormState) : HomeAssistantUiState

    /** Configuré : liste des entités commandables. */
    data class Entities(
        val items: List<HomeAssistantEntity>,
        val isRefreshing: Boolean = false,
        val listError: String? = null,
        val transientError: String? = null
    ) : HomeAssistantUiState
}
