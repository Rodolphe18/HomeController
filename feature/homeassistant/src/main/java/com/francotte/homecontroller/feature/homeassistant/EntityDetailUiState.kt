package com.francotte.homecontroller.feature.homeassistant

/** État de l'écran de détail d'une entité. */
sealed interface EntityDetailUiState {
    data object Loading : EntityDetailUiState
    data class Error(val message: String) : EntityDetailUiState
    data class Content(
        val friendlyName: String,
        val isOn: Boolean,
        val supportsBrightness: Boolean,
        val brightnessPercent: Int,       // luminosité AFFICHÉE (drag optimiste inclus)
        val transientError: String? = null
    ) : EntityDetailUiState
}
