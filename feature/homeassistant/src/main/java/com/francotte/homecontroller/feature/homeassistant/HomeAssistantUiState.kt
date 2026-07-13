package com.francotte.homecontroller.feature.homeassistant

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.francotte.homecontroller.core.model.HomeAssistantEntity

/** État du formulaire de configuration — porté par [HomeAssistantConfigurationViewModel]. */
@Immutable
data class ConfigurationFormState(
    val url: String = "",
    val token: String = "",
    val isTesting: Boolean = false,
    @param:StringRes val error: Int? = null
)

/**
 * État « aiguilleur » du parcours Home Assistant, produit par [HomeAssistantViewModel].
 * Il ne décide QUE de l'écran à afficher ; le contenu de chaque écran vit dans son propre VM.
 */
sealed interface HomeAssistantUiState {
    /** Lecture initiale de la configuration. */
    data object Loading : HomeAssistantUiState

    /**
     * Écran de configuration. [canCancel] vaut true en édition (une config existe déjà : on peut
     * annuler et revenir à la liste) ; false lors de la configuration initiale (rien derrière).
     */
    @Immutable
    data class Unconfigured(val canCancel: Boolean = false) : HomeAssistantUiState

    /** Écran des entités commandables. */
    data object Entities : HomeAssistantUiState
}

/** État de l'écran des entités — porté par [HomeAssistantEntitiesViewModel]. */
sealed interface EntitiesUiState {
    /** Chargement initial de la liste. */
    data object Loading : EntitiesUiState

    /** Liste chargée (éventuellement vide), avec état de refresh et erreurs éventuelles. */
    @Immutable
    data class Content(
        val items: List<HomeAssistantEntity>,
        val isRefreshing: Boolean = false,
        @param:StringRes val listError: Int? = null,
        @param:StringRes val transientError: Int? = null
    ) : EntitiesUiState
}
