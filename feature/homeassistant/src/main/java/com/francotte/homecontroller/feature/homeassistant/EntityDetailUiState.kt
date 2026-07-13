package com.francotte.homecontroller.feature.homeassistant

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** État de l'écran de détail d'une entité. */
sealed interface EntityDetailUiState {
    data object Loading : EntityDetailUiState

    @Immutable
    data class Error(@param:StringRes val messageRes: Int) : EntityDetailUiState

    @Immutable
    data class Content(
        val friendlyName: String,
        val isOn: Boolean,
        val supportsBrightness: Boolean,
        val brightnessPercent: Int,       // luminosité AFFICHÉE (drag optimiste inclus)
        @param:StringRes val transientError: Int? = null
    ) : EntityDetailUiState
}
